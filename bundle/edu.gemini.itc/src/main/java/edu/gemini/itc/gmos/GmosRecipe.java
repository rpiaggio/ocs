package edu.gemini.itc.gmos;

import edu.gemini.itc.operation.*;
import edu.gemini.itc.shared.*;
import edu.gemini.itc.web.HtmlPrinter;
import edu.gemini.itc.web.ITCRequest;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * This class performs the calculations for Gmos used for imaging.
 */
public final class GmosRecipe extends RecipeBase {

    private final Calendar now = Calendar.getInstance();
    private final String _header = "# GMOS ITC: " + now.getTime() + "\n";

    // Parameters from the web page.
    private final SourceDefinition _sdParameters;
    private final ObservationDetails _obsDetailParameters;
    private final ObservingConditions _obsConditionParameters;
    private final GmosParameters _gmosParameters;
    private final TelescopeDetails _telescope;
    private final PlottingDetails _plotParameters;

    /**
     * Constructs a GmosRecipe by parsing a Multipart servlet request.
     *
     * @param r   Servlet request containing form data from ITC web page.
     * @param out Results will be written to this PrintWriter.
     * @throws Exception on failure to parse parameters.
     */
    public GmosRecipe(final ITCMultiPartParser r, final PrintWriter out) {
        super(out);

        // Read parameters from the four main sections of the web page.
        _sdParameters = ITCRequest.sourceDefinitionParameters(r);
        _obsDetailParameters = ITCRequest.observationParameters(r);
        _obsConditionParameters = ITCRequest.obsConditionParameters(r);
        _gmosParameters = ITCRequest.gmosParameters(r);
        _telescope = ITCRequest.teleParameters(r);
        _plotParameters = ITCRequest.plotParamters(r);

        validateInputParamters();
    }

    /**
     * Constructs a GmosRecipe given the parameters. Useful for testing.
     */
    public GmosRecipe(final SourceDefinition sdParameters,
                      final ObservationDetails obsDetailParameters,
                      final ObservingConditions obsConditionParameters,
                      final GmosParameters gmosParameters, TelescopeDetails telescope,
                      final PlottingDetails plotParameters,
                      final PrintWriter out)

    {
        super(out);
        _sdParameters = sdParameters;
        _obsDetailParameters = obsDetailParameters;
        _obsConditionParameters = obsConditionParameters;
        _gmosParameters = gmosParameters;
        _telescope = telescope;
        _plotParameters = plotParameters;

        validateInputParamters();
    }

    private void validateInputParamters() {
        if (_sdParameters.getDistributionType().equals(SourceDefinition.Distribution.ELINE)) {
            if (_sdParameters.getELineWidth() < (3E5 / (_sdParameters.getELineWavelength() * 1000))) {
                throw new RuntimeException(
                        "Please use a model line width > 1 nm (or "
                                + (3E5 / (_sdParameters.getELineWavelength() * 1000))
                                + " km/s) to avoid undersampling of the line profile when convolved with the transmission response");
            }
        }

        // report error if this does not come out to be an integer
        checkSourceFraction(_obsDetailParameters.getNumExposures(), _obsDetailParameters.getSourceFraction());
    }

    /**
     * Performes recipe calculation and writes results to a cached PrintWriter or to System.out.
     */
    public void writeOutput() {
        final Gmos mainInstrument = createGmos();
        if (_obsDetailParameters.getMethod().isSpectroscopy()) {
            final SpectroscopyResult[] results = calculateSpectroscopy(mainInstrument);
            writeSpectroscopyOutput(mainInstrument, results);
        } else {
            final ImagingResult[] results = calculateImaging(mainInstrument);
            writeImagingOutput(mainInstrument, results);
        }
    }

    public SpectroscopyResult[] calculateSpectroscopy() {
        return calculateSpectroscopy(createGmos());
    }

    public RecipeBase.ImagingResult[] calculateImaging() {
        return calculateImaging(createGmos());
    }

    private SpectroscopyResult[] calculateSpectroscopy(final Gmos mainInstrument) {
        final Gmos[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        final SpectroscopyResult[] results = new SpectroscopyResult[ccdArray.length];
        for (int i = 0; i < ccdArray.length; i++) {
            final Gmos instrument = ccdArray[i];
            results[i] = calculateSpectroscopy(mainInstrument, instrument, ccdArray.length);
        }
        return results;
    }

    private ImagingResult[] calculateImaging(final Gmos mainInstrument) {
        final Gmos[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        final ImagingResult[] results = new ImagingResult[ccdArray.length];
        for (int i = 0; i < ccdArray.length; i++) {
            final Gmos instrument = ccdArray[i];
            results[i] = calculateImagingDo(instrument);
        }
        return results;
    }

    private Gmos createGmos() {
        switch (_gmosParameters.site()) {
            case GN: return new GmosNorth(_gmosParameters, _obsDetailParameters, 0);
            case GS: return new GmosSouth(_gmosParameters, _obsDetailParameters, 0);
            default: throw new Error("invalid site");
        }
    }

    // TODO: bring mainInstrument and instrument together
    private SpectroscopyResult calculateSpectroscopy(final Gmos mainInstrument, final Gmos instrument, final int detectorCount) {

        final SpecS2NLargeSlitVisitor[] specS2N;
        final SlitThroughput st;

        final SEDFactory.SourceResult src = SEDFactory.calculate(instrument, _gmosParameters.site(), ITCConstants.VISIBLE, _sdParameters, _obsConditionParameters, _telescope, _plotParameters);
        final int ccdIndex = instrument.getDetectorCcdIndex();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();
        final int firstCcdIndex = tv.getDetectorCcdStartIndex(ccdIndex);
        final int lastCcdIndex = tv.getDetectorCcdEndIndex(ccdIndex, detectorCount);

        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification

        final double pixel_size = instrument.getPixelSize();
        double ap_diam;
        double source_fraction;
        List<Double> sf_list = new ArrayList<>();

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();
        double im_qual = IQcalc.getImageQuality();

        final SourceFraction SFcalc = SourceFractionFactory.calculate(_sdParameters, _obsDetailParameters, instrument, im_qual);
        if (!instrument.isIfuUsed()) {
            source_fraction = SFcalc.getSourceFraction();
        } else {
            final VisitableMorphology morph;
            if (!_sdParameters.isUniform()) {
                morph = new GaussianMorphology(im_qual);
            } else {
                morph = new USBMorphology();
            }
            morph.accept(instrument.getIFU().getAperture());
            // for now just a single item from the list
            sf_list = instrument.getIFU().getFractionOfSourceInAperture();
            source_fraction = sf_list.get(0);
        }


        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.
        double spec_source_frac;
        final int number_exposures = _obsDetailParameters.getNumExposures();
        final double frac_with_source = _obsDetailParameters.getSourceFraction();
        final double dark_current = instrument.getDarkCurrent();
        final double exposure_time = _obsDetailParameters.getExposureTime();
        final double read_noise = instrument.getReadNoise();

        // ObservationMode Imaging or spectroscopy
        if (!instrument.isIfuUsed()) {
            if (!_obsDetailParameters.isAutoAperture()) {
                st = new SlitThroughput(im_qual, _obsDetailParameters.getApertureDiameter(), pixel_size, _gmosParameters.slitWidth());
            } else {
                st = new SlitThroughput(im_qual, pixel_size, _gmosParameters.slitWidth());
            }
            ap_diam = st.getSpatialPix();
            spec_source_frac = st.getSlitThroughput();
        } else {
            st = null; // TODO: how to deal with no ST in case of IFU?
            spec_source_frac = source_fraction;
            ap_diam = 5 / instrument.getSpatialBinning();
        }

        // For the usb case we want the resolution to be determined by the
        // slit width and not the image quality for a point source.
        if (_sdParameters.isUniform()) {
            im_qual = 10000;

            if (!instrument.isIfuUsed()) {

                if (!_obsDetailParameters.isAutoAperture()) {
                    spec_source_frac = _gmosParameters.slitWidth() * ap_diam * pixel_size;
                } else {
                    ap_diam = new Double(1 / (_gmosParameters.slitWidth() * pixel_size) + 0.5).intValue();
                    spec_source_frac = 1;
                }
            }
        }

        if (instrument.isIfuUsed() && !_sdParameters.isUniform()) {
            specS2N = new SpecS2NLargeSlitVisitor[sf_list.size()];
            for (int i = 0; i < sf_list.size(); i++) {
                final double spsf = sf_list.get(i);
                specS2N[i] = new SpecS2NLargeSlitVisitor(
                        _gmosParameters.slitWidth(),
                        pixel_size,
                        instrument.getSpectralPixelWidth(),
                        instrument.getObservingStart(),
                        instrument.getObservingEnd(),
                        instrument.getGratingDispersion_nm(),
                        instrument.getGratingDispersion_nmppix(),
                        instrument.getGratingResolution(),
                        spsf,
                        im_qual,
                        ap_diam,
                        number_exposures,
                        frac_with_source,
                        exposure_time,
                        dark_current * instrument.getSpatialBinning() * instrument.getSpectralBinning(),
                        read_noise,
                        _obsDetailParameters.getSkyApertureDiameter());

                specS2N[i].setDetectorTransmission(mainInstrument.getDetectorTransmision());
                specS2N[i].setCcdPixelRange(firstCcdIndex, lastCcdIndex);
                specS2N[i].setSourceSpectrum(src.sed);
                specS2N[i].setBackgroundSpectrum(src.sky);
                src.sed.accept(specS2N[i]);

            }
        } else {
            specS2N = new SpecS2NLargeSlitVisitor[1];
            specS2N[0] = new SpecS2NLargeSlitVisitor(
                    _gmosParameters.slitWidth(),
                    pixel_size,
                    instrument.getSpectralPixelWidth(),
                    instrument.getObservingStart(),
                    instrument.getObservingEnd(),
                    instrument.getGratingDispersion_nm(),
                    instrument.getGratingDispersion_nmppix(),
                    instrument.getGratingResolution(),
                    spec_source_frac,
                    im_qual,
                    ap_diam,
                    number_exposures,
                    frac_with_source,
                    exposure_time,
                    dark_current * instrument.getSpatialBinning() * instrument.getSpectralBinning(),
                    read_noise,
                    _obsDetailParameters.getSkyApertureDiameter());

            specS2N[0].setDetectorTransmission(mainInstrument.getDetectorTransmision());
            specS2N[0].setCcdPixelRange(firstCcdIndex, lastCcdIndex);
            specS2N[0].setSourceSpectrum(src.sed);
            specS2N[0].setBackgroundSpectrum(src.sky);
            src.sed.accept(specS2N[0]);

        }

        return new SpectroscopyResult(SFcalc, IQcalc, specS2N, st);

    }

    private ImagingResult calculateImagingDo(final Gmos instrument) {

        // Start of morphology section of ITC

        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio. There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification

        final SEDFactory.SourceResult src = SEDFactory.calculate(instrument, _gmosParameters.site(), ITCConstants.VISIBLE, _sdParameters, _obsConditionParameters, _telescope, _plotParameters);
        final double sed_integral = src.sed.getIntegral();
        final double sky_integral = src.sky.getIntegral();

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();
        final double im_qual = IQcalc.getImageQuality();

        // Calculate the Fraction of source in the aperture
        final SourceFraction SFcalc = SourceFractionFactory.calculate(_sdParameters, _obsDetailParameters, instrument, im_qual);

        // Calculate the Peak Pixel Flux
        final double peak_pixel_count = PeakPixelFlux.calculate(instrument, _sdParameters, _obsDetailParameters, SFcalc, im_qual, sed_integral, sky_integral);

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.
        final int number_exposures = _obsDetailParameters.getNumExposures();
        final double frac_with_source = _obsDetailParameters.getSourceFraction();
        // report error if this does not come out to be an integer
        checkSourceFraction(number_exposures, frac_with_source);

        final ImagingS2NCalculatable IS2Ncalc = ImagingS2NCalculationFactory.getCalculationInstance(_obsDetailParameters, instrument, SFcalc, sed_integral, sky_integral);
        IS2Ncalc.calculate();

        return new ImagingResult(IQcalc, SFcalc, peak_pixel_count, IS2Ncalc);

    }

    // ===================================================================================================================
    // TODO: OUTPUT METHODS
    // TODO: These need to be simplified/cleaned/shared and then go to the web module.. and then be deleted and forgotten.
    // ===================================================================================================================


    private void writeSpectroscopyOutput(final Gmos mainInstrument, final SpectroscopyResult[] results) {
        _println("");

        // This object is used to format numerical strings.
        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // Two decimal places
        device.clear();

        // Create one chart to use for all 3 CCDS (one for Signal and Background and one for Intermediate Single Exp and Final S/N)
        final ITCChart gmosChart1;
        final ITCChart gmosChart2;
        final boolean ifuAndNotUniform = mainInstrument.isIfuUsed() && !(_sdParameters.isUniform());
        final double ifu_offset = ifuAndNotUniform ? mainInstrument.getIFU().getApertureOffsetList().iterator().next() : 0.0;
        final String chart1Title = ifuAndNotUniform ? "Signal and Background (IFU element offset: " + device.toString(ifu_offset) + " arcsec)" : "Signal and Background ";
        final String chart2Title = ifuAndNotUniform ? "Intermediate Single Exp and Final S/N (IFU element offset: " + device.toString(ifu_offset) + " arcsec)" : "Intermediate Single Exp and Final S/N";
        gmosChart1 = new ITCChart(chart1Title, "Wavelength (nm)", "e- per exposure per spectral pixel", _plotParameters);
        gmosChart2 = new ITCChart(chart2Title, "Wavelength (nm)", "Signal / Noise per spectral pixel", _plotParameters);

        String sigSpec = null, backSpec = null, singleS2N = null, finalS2N = null;
        final Gmos[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        final DetectorsTransmissionVisitor tv = mainInstrument.getDetectorTransmision();
        final int detectorCount = ccdArray.length;

        for (final Gmos instrument : ccdArray) {

            final int ccdIndex = instrument.getDetectorCcdIndex();
            final String ccdName = instrument.getDetectorCcdName();
            final Color ccdColor = instrument.getDetectorCcdColor();
            final Color ccdColorDarker = ccdColor == null ? null : ccdColor.darker().darker();
            final int firstCcdIndex = tv.getDetectorCcdStartIndex(ccdIndex);
            final int lastCcdIndex = tv.getDetectorCcdEndIndex(ccdIndex, detectorCount);
            // REL-478: include the gaps in the text data output
            final int lastCcdIndexWithGap = (ccdIndex < 2 && detectorCount > 1)
                    ? tv.getDetectorCcdStartIndex(ccdIndex + 1)
                    : lastCcdIndex;

            final SpectroscopyResult calcGmos = results[ccdIndex];

            final int number_exposures = _obsDetailParameters.getNumExposures();
            final double frac_with_source = _obsDetailParameters.getSourceFraction();
            final double exposure_time = _obsDetailParameters.getExposureTime();

            if (ccdIndex == 0) {
                _println("Read noise: " + instrument.getReadNoise());
                if (!instrument.isIfuUsed()) {
                    if (!_obsDetailParameters.isAutoAperture()) {
                        _println("software aperture extent along slit = " + device.toString(_obsDetailParameters.getApertureDiameter()) + " arcsec");
                    } else {
                        switch (_sdParameters.getProfileType()) {
                            case UNIFORM:
                                _println("software aperture extent along slit = " + device.toString(1 / _gmosParameters.slitWidth()) + " arcsec");
                                break;
                            case POINT:
                                _println("software aperture extent along slit = " + device.toString(1.4 * calcGmos.IQcalc.getImageQuality()) + " arcsec");
                                break;
                        }
                    }

                    if (!_sdParameters.isUniform()) {
                        _println("fraction of source flux in aperture = " + device.toString(calcGmos.st.getSlitThroughput()));
                    }
                }
                _println("derived image size(FWHM) for a point source = " + device.toString(calcGmos.IQcalc.getImageQuality()) + "arcsec\n");
                _println("Sky subtraction aperture = " + _obsDetailParameters.getSkyApertureDiameter() + " times the software aperture.");
                _println("");
                _println("Requested total integration time = " + device.toString(exposure_time * number_exposures) + " secs, of which " + device.toString(exposure_time * number_exposures * frac_with_source) + " secs is on source.");
                _print("<HR align=left SIZE=3>");
            }

            // For IFUs we can have more than one S2N result.
            for (int i = 0; i < calcGmos.specS2N.length; i++) {

                gmosChart1.addArray(calcGmos.specS2N[i].getSignalSpectrum().getData(firstCcdIndex, lastCcdIndex), "Signal " + ccdName, ccdColor);
                gmosChart1.addArray(calcGmos.specS2N[i].getBackgroundSpectrum().getData(firstCcdIndex, lastCcdIndex), "SQRT(Background) " + ccdName, ccdColorDarker);

                gmosChart2.addArray(calcGmos.specS2N[i].getExpS2NSpectrum().getData(firstCcdIndex, lastCcdIndex), "Single Exp S/N " + ccdName, ccdColor);
                gmosChart2.addArray(calcGmos.specS2N[i].getFinalS2NSpectrum().getData(firstCcdIndex, lastCcdIndex), "Final S/N " + ccdName, ccdColorDarker);

                if (ccdIndex == 0) {
                    _println("<p style=\"page-break-inside: never\">");
                    sigSpec = _printSpecTag("ASCII signal spectrum");
                    backSpec = _printSpecTag("ASCII background spectrum");
                    singleS2N = _printSpecTag("Single Exposure S/N ASCII data");
                    finalS2N = _printSpecTag("Final S/N ASCII data");
                }
                _println("");
            }

            _println(calcGmos.specS2N[calcGmos.specS2N.length-1].getSignalSpectrum(), _header, sigSpec, firstCcdIndex, lastCcdIndexWithGap);
            _println(calcGmos.specS2N[calcGmos.specS2N.length-1].getBackgroundSpectrum(), _header, backSpec, firstCcdIndex, lastCcdIndexWithGap);
            _println(calcGmos.specS2N[calcGmos.specS2N.length-1].getExpS2NSpectrum(), _header, singleS2N, firstCcdIndex, lastCcdIndexWithGap);
            _println(calcGmos.specS2N[calcGmos.specS2N.length-1].getFinalS2NSpectrum(), _header, finalS2N, firstCcdIndex, lastCcdIndexWithGap);

        }

        _println(gmosChart1.getBufferedImage(), "SigAndBack");
        _println("");
        _println(gmosChart2.getBufferedImage(), "Sig2N");
        _println("");

        printConfiguration(mainInstrument);
    }


    private void writeImagingOutput(final Gmos mainInstrument, final ImagingResult[] results) {
        _println("");

        // This object is used to format numerical strings.
        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // Two decimal places
        device.clear();


        final Gmos[] ccdArray = mainInstrument.getDetectorCcdInstruments();
        for (final Gmos instrument : ccdArray) {
            final int ccdIndex = instrument.getDetectorCcdIndex();
            final String ccdName = instrument.getDetectorCcdName();
            final String forCcdName = ccdName.length() == 0 ? "" : " for " + ccdName;

            final ImagingResult calcGmos = results[ccdIndex];

            if (ccdIndex == 0) {
                _print(calcGmos.SFcalc.getTextResult(device));
                _println(calcGmos.IQcalc.getTextResult(device));
                _println("Sky subtraction aperture = "
                        + _obsDetailParameters.getSkyApertureDiameter()
                        + " times the software aperture.\n");
                _println("Read noise: " + instrument.getReadNoise());
            }
            _println("");
            _println("<b>S/N" + forCcdName + ":</b>");
            _println("");
            _println(calcGmos.IS2Ncalc.getTextResult(device));

            device.setPrecision(0); // NO decimal places
            device.clear();
            final int binFactor = instrument.getSpatialBinning() * instrument.getSpatialBinning();

            _println("");
            _println("The peak pixel signal + background is " + device.toString(calcGmos.peak_pixel_count) + ". ");

            if (calcGmos.peak_pixel_count > (.95 * instrument.getWellDepth() * binFactor))
                _println("Warning: peak pixel may be saturating the (binned) CCD full well of "
                        + .95 * instrument.getWellDepth() * binFactor);

            if (calcGmos.peak_pixel_count > (.95 * instrument.getADSaturation() * instrument.getLowGain()))
                _println("Warning: peak pixel may be saturating the low gain setting of "
                        + .95
                        * instrument.getADSaturation()
                        * instrument.getLowGain());

            if (calcGmos.peak_pixel_count > (.95 * instrument.getADSaturation() * instrument.getHighGain()))
                _println("Warning: peak pixel may be saturating the high gain setting "
                        + .95
                        * instrument.getADSaturation()
                        * instrument.getHighGain());

        }

        printConfiguration(mainInstrument);
    }

    private void printConfiguration(final Gmos mainInstrument) {
        _println("");

        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // TWO decimal places
        device.clear();

        _print("<HR align=left SIZE=3>");

        _println(HtmlPrinter.printParameterSummary(_plotParameters));

        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + mainInstrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(_sdParameters));
        _println(mainInstrument.toString());
        _println(HtmlPrinter.printParameterSummary(_telescope));
        _println(HtmlPrinter.printParameterSummary(_obsConditionParameters));
        _println(HtmlPrinter.printParameterSummary(_obsDetailParameters));
    }


}

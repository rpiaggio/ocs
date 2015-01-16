// This software is Copyright(c) 2010 Association of Universities for
// Research in Astronomy, Inc.  This software was prepared by the
// Association of Universities for Research in Astronomy, Inc. (AURA)
// acting as operator of the Gemini Observatory under a cooperative
// agreement with the National Science Foundation. This software may 
// only be used or copied as described in the license set out in the 
// file LICENSE.TXT included with the distribution package.
//
// $Id: AcqCamRecipe.java,v 1.6 2004/02/16 18:49:01 bwalls Exp $
//
package edu.gemini.itc.acqcam;

import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import edu.gemini.itc.shared.FormatStringWriter;
import edu.gemini.itc.shared.ITCConstants;
import edu.gemini.itc.shared.RecipeBase;
import edu.gemini.itc.shared.SampledSpectrumVisitor;
import edu.gemini.itc.shared.SEDFactory;
import edu.gemini.itc.shared.VisitableSampledSpectrum;
import edu.gemini.itc.shared.WavebandDefinition;
import edu.gemini.itc.shared.ITCMultiPartParser;


import edu.gemini.itc.parameters.SourceDefinitionParameters;
import edu.gemini.itc.parameters.ObservationDetailsParameters;
import edu.gemini.itc.parameters.ObservingConditionParameters;
import edu.gemini.itc.parameters.TeleParameters;

import edu.gemini.itc.operation.ResampleVisitor;
import edu.gemini.itc.operation.RedshiftVisitor;
import edu.gemini.itc.operation.AtmosphereVisitor;
import edu.gemini.itc.operation.TelescopeApertureVisitor;
import edu.gemini.itc.operation.TelescopeTransmissionVisitor;
import edu.gemini.itc.operation.TelescopeBackgroundVisitor;
import edu.gemini.itc.operation.NormalizeVisitor;
import edu.gemini.itc.operation.CloudTransmissionVisitor;
import edu.gemini.itc.operation.WaterTransmissionVisitor;
import edu.gemini.itc.operation.PeakPixelFluxCalc;
import edu.gemini.itc.operation.ImageQualityCalculatable;
import edu.gemini.itc.operation.ImageQualityCalculationFactory;
import edu.gemini.itc.operation.SourceFractionCalculationFactory;
import edu.gemini.itc.operation.SourceFractionCalculatable;
import edu.gemini.itc.operation.ImagingS2NCalculationFactory;
import edu.gemini.itc.operation.ImagingS2NCalculatable;

/**
 * This class performs the calculations for the Acquisition Camera
 * used for imaging.
 */
public final class AcqCamRecipe extends RecipeBase {    
    // Parameters from the web page.
    private SourceDefinitionParameters _sdParameters;
    private ObservationDetailsParameters _obsDetailParameters;
    private ObservingConditionParameters _obsConditionParameters;
    private AcquisitionCamParameters _acqCamParameters;
    private TeleParameters _teleParameters;
    
    /**
     * Constructs an AcqCamRecipe by parsing servlet request.
     * @param r Servlet request containing form data from ITC web page.
     * @param out Results will be written to this PrintWriter.
     * @throws Exception on failure to parse parameters.
     */
    public AcqCamRecipe(HttpServletRequest r, PrintWriter out) throws Exception {
        super (out);
        
        // Read parameters from the four main sections of the web page.
        _sdParameters = new SourceDefinitionParameters(r);
        _obsDetailParameters = new ObservationDetailsParameters(r);
        _obsConditionParameters = new ObservingConditionParameters(r);
        _acqCamParameters = new AcquisitionCamParameters(r);
        _teleParameters = new TeleParameters(r);
    }
    
    /**
     * Constructs an AcqCamRecipe by parsing a Multi part servlet request.
     * @param r Servlet request containing form data from ITC web page.
     * @param out Results will be written to this PrintWriter.
     * @throws Exception on failure to parse parameters.
     */
    public AcqCamRecipe(ITCMultiPartParser r, PrintWriter out) throws Exception {
        super (out);
        
        // Read parameters from the four main sections of the web page.
        _sdParameters = new SourceDefinitionParameters(r);
        _obsDetailParameters = new ObservationDetailsParameters(r);
        _obsConditionParameters = new ObservingConditionParameters(r);
        _acqCamParameters = new AcquisitionCamParameters(r);
        _teleParameters = new TeleParameters(r);
    }
    
    /**
     * Constructs an AcqCamRecipe given the parameters.
     * Useful for testing.
     */
    public AcqCamRecipe(SourceDefinitionParameters sdParameters,
            ObservationDetailsParameters obsDetailParameters,
            ObservingConditionParameters obsConditionParameters,
            AcquisitionCamParameters acqCamParameters,
            TeleParameters teleParameters,
            PrintWriter out) {
        super(out);

        _sdParameters = sdParameters;
        _obsDetailParameters = obsDetailParameters;
        _obsConditionParameters = obsConditionParameters;
        _acqCamParameters = acqCamParameters;
        _teleParameters = teleParameters;
    }
    
    /**
     * Performes recipe calculation and writes results to a cached PrintWriter
     * or to System.out.
     * @throws Exception A recipe calculation can fail in many ways,
     * missing data files, incorrectly-formatted data files, ...
     */
    public void writeOutput() throws Exception {
        // This object is used to format numerical strings.
        FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2);  // Two decimal places
        device.clear();
        _println("");
        // For debugging, to be removed later
        //_print("<pre>" + _sdParameters.toString() + "</pre>");
        //_print("<pre>" + _acqCamParameters.toString() + "</pre>");
        //_print("<pre>" + _obsDetailParameters.toString() + "</pre>");
        //_print("<pre>" + _obsConditionParameters.toString() + "</pre>");
        
        // Module 1b
        // Define the source energy (as function of wavelength).
        //
        // inputs: instrument, SED
        // calculates: redshifted SED
        // output: redshifteed SED
        AcquisitionCamera instrument =
                new AcquisitionCamera(_acqCamParameters.getColorFilter(),
                _acqCamParameters.getNDFilter());
        
        
        if (_sdParameters.getSourceSpec().equals(_sdParameters.ELINE))
            if (_sdParameters.getELineWidth() < (3E5 / (_sdParameters.getELineWavelength()*1000))) {
            throw new Exception("Please use a model line width > 1 nm (or "+ (3E5 / (_sdParameters.getELineWavelength()*1000))+ " km/s) to avoid undersampling of the line profile when convolved with the transmission response");
            }
        
        //Get Source spectrum from factory
        VisitableSampledSpectrum sed =
                SEDFactory.getSED(_sdParameters,
                instrument);
        
        //Apply redshift if needed 
        SampledSpectrumVisitor redshift =
                new RedshiftVisitor(_sdParameters.getRedshift());
        sed.accept(redshift);
        
        // Must check to see if the redshift has moved the spectrum beyond
        // useful range.  The shifted spectrum must completely overlap
        // both the normalization waveband and the observation waveband
        // (filter region).
        
        String band = _sdParameters.getNormBand();
        double start = WavebandDefinition.getStart(band);
        double end = WavebandDefinition.getEnd(band);
        //System.out.println("WStart:" + start + "SStart:" +sed.getStart());
        //System.out.println("WEnd:" + end + "SEnd:" +sed.getEnd());
        //System.out.println("OStart:" + instrument.getObservingStart() + "OEnd:" +instrument.getObservingEnd());
        
        
        //any sed except BBODY and ELINE have normailization regions
        if (!(_sdParameters.getSpectrumResource().equals(_sdParameters.ELINE) ||
                _sdParameters.getSpectrumResource().equals(_sdParameters.BBODY))) {
            if (sed.getStart() > start || sed.getEnd() < end) {
                throw new Exception("Shifted spectrum lies outside of specified normalisation waveband.");
            }
        }
        
        if (sed.getStart() > instrument.getObservingStart() ||
                sed.getEnd() < instrument.getObservingEnd()) {
            _println(" Sed start" + sed.getStart() + "> than instrument start" + instrument.getObservingStart());
            _println(" Sed END" + sed.getEnd() + "< than instrument end" + instrument.getObservingEnd());
            
            throw new Exception("Shifted spectrum lies outside of observed wavelengths");
        }
        
        
        
        
        
        // Module 2
        // Convert input into standard internally-used units.
        //
        // inputs: instrument,redshifted SED, waveband, normalization flux, units
        // calculates: normalized SED, resampled SED, SED adjusted for aperture
        // output: SED in common internal units
        SampledSpectrumVisitor norm =
                new NormalizeVisitor(_sdParameters.getNormBand(),
                _sdParameters.getSourceNormalization(),
                _sdParameters.getUnits());
        if (!_sdParameters.getSpectrumResource().equals(_sdParameters.ELINE)) {
            sed.accept(norm);
        }
        
        
        // Resample the spectra for efficiency
        SampledSpectrumVisitor resample = new ResampleVisitor(
                instrument.getObservingStart(),
                instrument.getObservingEnd(),
                instrument.getSampling());
        //sed.accept(resample);
        
        //Create and apply Telescope aperture visitor
        SampledSpectrumVisitor tel = new TelescopeApertureVisitor();
        sed.accept(tel);
        
        // SED is now in units of photons/s/nm
        
        // Module 3b
        // The atmosphere and telescope modify the spectrum and
        // produce a background spectrum.
        //
        // inputs: SED, AIRMASS, sky emmision file, mirror configuration,
        // output: SED and sky background as they arrive at instruments
        
        SampledSpectrumVisitor atmos =
                new AtmosphereVisitor(_obsConditionParameters.getAirmass());
        //sed.accept(atmos);
        
        SampledSpectrumVisitor clouds = new CloudTransmissionVisitor(
                _obsConditionParameters.getSkyTransparencyCloud());
        sed.accept(clouds);
        
        
        SampledSpectrumVisitor water = new WaterTransmissionVisitor(
                _obsConditionParameters.getSkyTransparencyWater(),
                _obsConditionParameters.getAirmass(),
                "skytrans_", ITCConstants.MAUNA_KEA, ITCConstants.VISIBLE);
        sed.accept(water);
        
        
        // Background spectrum is introduced here.
        VisitableSampledSpectrum sky =
                SEDFactory.getSED(ITCConstants.SKY_BACKGROUND_LIB + "/" +
                ITCConstants.OPTICAL_SKY_BACKGROUND_FILENAME_BASE + "_"
                + _obsConditionParameters.getSkyBackgroundCategory() +
                "_" + _obsConditionParameters.getAirmassCategory()
                + ITCConstants.DATA_SUFFIX,
                instrument.getSampling());
        
        
        //Create and Add Background for the tele
        
        SampledSpectrumVisitor tb =
                new TelescopeBackgroundVisitor(_teleParameters.getMirrorCoating(),
                _teleParameters.getInstrumentPort(),
                ITCConstants.MAUNA_KEA, ITCConstants.VISIBLE);
        sky.accept(tb);
        
        
        // Apply telescope transmission
        SampledSpectrumVisitor t =
                new TelescopeTransmissionVisitor(_teleParameters.getMirrorCoating(),
                _teleParameters.getInstrumentPort());
        
        sed.accept(t);
        sky.accept(t);
        
        
        sky.accept(tel);
        
        // Add instrument background to sky background for a total background.
        // At this point "sky" is not the right name
        
        instrument.addBackground(sky);
        
        // Module 4  AO module not implemented
        // The AO module affects source and background SEDs.
        
        // Module 5b
        // The instrument with its detectors modifies the source and
        // background spectra.
        // input: instrument, source and background SED
        // output: total flux of source and background.
        
        
        instrument.convolveComponents(sed);
        instrument.convolveComponents(sky);
        
        //ITCPlot plot2 = new ITCPlot(sky.getDataSource());
        //plot2.addDataSource(sed.getDataSource());
        //plot2.disp();
        
        
        
        double sed_integral = sed.getIntegral();
        double sky_integral = sky.getIntegral();
        
        // For debugging, print the spectrum integrals.
        //_println("SED integral: "+sed_integral+"\tSKY integral: "+sky_integral);
        
        // End of the Spectral energy distribution portion of the ITC.
        
        
        // Start of morphology section of ITC
        
        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio.  There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification
        
        String ap_type = _obsDetailParameters.getApertureType();
        double pixel_size = instrument.getPixelSize();
        double ap_diam = 0;
        double ap_pix = 0;
        double sw_ap = 0;
        double Npix = 0;
        double source_fraction = 0;
        double pix_per_sq_arcsec = 0;
        double peak_pixel_count = 0;
        
        
        // Calculate image quality
        double im_qual = 0.;
        
        ImageQualityCalculationFactory IQcalcFactory = new ImageQualityCalculationFactory();
        ImageQualityCalculatable IQcalc =
                (ImageQualityCalculatable) IQcalcFactory.getCalculationInstance(
                _sdParameters, _obsDetailParameters, _obsConditionParameters,
                _teleParameters, instrument);
        IQcalc.calculate();
        
        im_qual = IQcalc.getImageQuality();
        
        
        
//Calculate Source fraction
        SourceFractionCalculationFactory SFcalcFactory = new SourceFractionCalculationFactory();
        SourceFractionCalculatable SFcalc =
                (SourceFractionCalculatable) SFcalcFactory.getCalculationInstance(
                _sdParameters, _obsDetailParameters, _obsConditionParameters,
                _teleParameters, instrument);
        SFcalc.setImageQuality(im_qual);
        SFcalc.calculate();
        _print(SFcalc.getTextResult(device));
        _println(IQcalc.getTextResult(device));
        
// Calculate the Peak Pixel Flux
        PeakPixelFluxCalc ppfc;
        
        if (_sdParameters.getSourceGeometry().
                equals(SourceDefinitionParameters.POINT_SOURCE) ||
                _sdParameters.getExtendedSourceType().
                equals(SourceDefinitionParameters.GAUSSIAN)) {
            
            ppfc = new
                    PeakPixelFluxCalc(im_qual, pixel_size,
                    _obsDetailParameters.getExposureTime(),
                    sed_integral, sky_integral,
                    instrument.getDarkCurrent());
            
            peak_pixel_count = ppfc.getFluxInPeakPixel();
        } else if (_sdParameters.getExtendedSourceType().
                equals(SourceDefinitionParameters.UNIFORM)) {
            double usbApArea = 0;
            ppfc = new
                    PeakPixelFluxCalc(im_qual, pixel_size,
                    _obsDetailParameters.getExposureTime(),
                    sed_integral, sky_integral,
                    instrument.getDarkCurrent());
            peak_pixel_count = ppfc.getFluxInPeakPixelUSB(SFcalc.getSourceFraction(),SFcalc.getNPix());
        } else {
            throw new Exception(
                    "Peak Pixel Flux could not be calculated for type" +
                    _sdParameters.getSourceGeometry());
        }
        
        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.
        
        
        
        // Observation method
        
        int number_exposures = _obsDetailParameters.getNumExposures();
        double frac_with_source = _obsDetailParameters.getSourceFraction();
        
        // report error if this does not come out to be an integer
	    checkSourceFraction(number_exposures, frac_with_source);
        
        double exposure_time = _obsDetailParameters.getExposureTime();
        double dark_current = instrument.getDarkCurrent();
        double read_noise = instrument.getReadNoise();
        
        //Calculate the Signal to Noise
        
        ImagingS2NCalculationFactory IS2NcalcFactory = new ImagingS2NCalculationFactory();
        ImagingS2NCalculatable IS2Ncalc =
                (ImagingS2NCalculatable) IS2NcalcFactory.getCalculationInstance(
                _sdParameters, _obsDetailParameters, _obsConditionParameters,
                _teleParameters, instrument);
        IS2Ncalc.setSedIntegral(sed_integral);
        IS2Ncalc.setSkyIntegral(sky_integral);
        IS2Ncalc.setSkyAperture(_obsDetailParameters.getSkyApertureDiameter());
        IS2Ncalc.setSourceFraction(SFcalc.getSourceFraction());
        IS2Ncalc.setNpix(SFcalc.getNPix());
        IS2Ncalc.setDarkCurrent(instrument.getDarkCurrent());
        IS2Ncalc.calculate();
        _println(IS2Ncalc.getTextResult(device));
        //_println(IS2Ncalc.getBackgroundLimitResult());
        device.setPrecision(0);  // NO decimal places
        device.clear();
        
        _println("");
        _println("The peak pixel signal + background is " + device.toString(peak_pixel_count) + ". This is " +
                device.toString(peak_pixel_count / instrument.getWellDepth() * 100) +
                "% of the full well depth of " + device.toString(instrument.getWellDepth()) + ".");
        
        if (peak_pixel_count > (.8 * instrument.getWellDepth()))
            _println("Warning: peak pixel exceeds 80% of the well depth and may be saturated");
        
        _println("");
        device.setPrecision(2);  // TWO decimal places
        device.clear();
        
        
        
        ///////////////////////////////////////////////
        //////////Print Config////////////////////////
        
        _print("<HR align=left SIZE=3>");
        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + instrument.getName() + "\n");
        _println(_sdParameters.printParameterSummary());
        _println(instrument.toString());
        _println(_teleParameters.printParameterSummary());
        _println(_obsConditionParameters.printParameterSummary());
        _println(_obsDetailParameters.printParameterSummary());        
        
    }    
}
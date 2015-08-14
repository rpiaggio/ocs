package edu.gemini.itc.nifs;

import edu.gemini.itc.base.*;
import edu.gemini.itc.operation.DetectorsTransmissionVisitor;
import edu.gemini.itc.shared.CalculationMethod;
import edu.gemini.itc.shared.ObservationDetails;

/**
 * Nifs specification class
 */
public final class Nifs extends Instrument {
    /**
     * Related files will be in this subdir of lib
     */
    public static final String INSTR_DIR = "nifs";

    /**
     * Related files will start with this prefix
     */
    public static final String INSTR_PREFIX = "nifs_";

    // Instrument reads its configuration from here.
    private static final String FILENAME = "nifs" + getSuffix();

    public static final int DETECTOR_PIXELS = 2048;

    // Keep a reference to the color filter to ask for effective wavelength
    protected Filter _Filter;
    protected NifsGratingOptics _gratingOptics;
    protected Detector _detector;
    protected double _sampling;
    protected CalculationMethod _mode;
    protected double _centralWavelength;

    protected String _IFUMethod;
    protected double _IFUOffset;
    protected double _IFUMinOffset;
    protected double _IFUMaxOffset;
    protected int _IFUNumX;
    protected int _IFUNumY;
    protected double _IFUCenterX;
    protected double _IFUCenterY;
    protected IFUComponent _IFU;
    protected boolean _IFU_IsSingle = false;
    protected boolean _IFU_IsSummed = false;

    protected DetectorsTransmissionVisitor _dtv;
    protected double _readNoiseValue;



    public Nifs(final NifsParameters gp, final ObservationDetails odp) {
        super(INSTR_DIR, FILENAME);

        // The instrument data file gives a start/end wavelength for
        // the instrument.  But with a filter in place, the filter
        // transmits wavelengths that are a subset of the original range.

        _sampling = super.getSampling();

        _centralWavelength = gp.getInstrumentCentralWavelength();
        _mode = odp.getMethod();

        if (_centralWavelength < 1000 || _centralWavelength > 6000) {
            throw new RuntimeException("Central wavelength must be between 1.00um and 6.0um.");
        }

        //Set read noise and Well depth values by obsevation type
        _readNoiseValue = gp.getReadMode().getReadNoise();

        _Filter = Filter.fromFile(getPrefix(), gp.getFilter().name(), getDirectory() + "/");
        addFilter(_Filter);

        FixedOptics _fixedOptics = new FixedOptics(getDirectory() + "/", getPrefix());
        addComponent(_fixedOptics);

        _detector = new Detector(getDirectory() + "/", getPrefix(), "hawaii2_HgCdTe", "2K x 2K HgCdTe HAWAII-2 CCD");
        _detector.setDetectorPixels(DETECTOR_PIXELS);

        _dtv = new DetectorsTransmissionVisitor(1, getDirectory() + "/" + getPrefix() + "ccdpix" + Instrument.getSuffix());

        _IFUMethod = gp.getIFUMethod();
        if (_IFUMethod.equals(gp.SINGLE_IFU)) {
            _IFU_IsSingle = true;
            _IFUOffset = gp.getIFUOffset();
            _IFU = new IFUComponent(_IFUOffset, getPixelSize());
        }
        if (_IFUMethod.equals(gp.RADIAL_IFU)) {
            _IFUMinOffset = gp.getIFUMinOffset();
            _IFUMaxOffset = gp.getIFUMaxOffset();

            _IFU = new IFUComponent(_IFUMinOffset, _IFUMaxOffset, getPixelSize());
        }
        if (_IFUMethod.equals(gp.SUMMED_APERTURE_IFU)) {
            _IFU_IsSummed = true;
            _IFUNumX = gp.getIFUNumX();
            _IFUNumY = gp.getIFUNumY();
            _IFUCenterX = gp.getIFUCenterX();
            _IFUCenterY = gp.getIFUCenterY();

            _IFU = new IFUComponent(_IFUNumX, _IFUNumY, _IFUCenterX, _IFUCenterY, getPixelSize());
        }
        addComponent(_IFU);


        _gratingOptics = new NifsGratingOptics(getDirectory() + "/" + getPrefix(), gp.getGrating().name(),
                _centralWavelength,
                _detector.getDetectorPixels(),
                1);
        _sampling = _gratingOptics.getGratingDispersion_nmppix();
        addGrating(_gratingOptics);


        addComponent(_detector);



    }

    /**
     * Returns the effective observing wavelength.
     * This is properly calculated as a flux-weighted average of
     * observed spectrum.  So this may be temporary.
     *
     * @return Effective wavelength in nm
     */
    public int getEffectiveWavelength() {
        return (int) _gratingOptics.getEffectiveWavelength();
    }


    /**
     * Returns the subdirectory where this instrument's data files are.
     */
    public String getDirectory() {
        return ITCConstants.LIB + "/" + INSTR_DIR;
    }

    public double getPixelSize() {
        return super.getPixelSize();
    }

    public double getSpectralPixelWidth() {
        return _gratingOptics.getPixelWidth();
    }

    public double getSampling() {
        return _sampling;
    }

    public IFUComponent getIFU() {
        return _IFU;
    }

    /**
     * The prefix on data file names for this instrument.
     */
    public static String getPrefix() {
        return INSTR_PREFIX;
    }

    public double getGratingResolution() {
        return _gratingOptics.getGratingResolution();
    }

    public double getGratingDispersion_nm() {
        return _gratingOptics.getGratingDispersion_nm();
    }

    public double getGratingDispersion_nmppix() {
        return _gratingOptics.getGratingDispersion_nmppix();
    }

    public DetectorsTransmissionVisitor getDetectorTransmision() {
        return _dtv;
    }

    public double getObservingStart() {
        return _centralWavelength - (getGratingDispersion_nmppix() * _detector.getDetectorPixels() / 2);
    }

    public double getObservingEnd() {
        return _centralWavelength + (getGratingDispersion_nmppix() * _detector.getDetectorPixels() / 2);
    }

    public double getIFUOffset() {
        return _IFUOffset;
    }

    public double getIFUMinOffset() {
        return _IFUMinOffset;
    }

    public double getIFUMaxOffset() {
        return _IFUMaxOffset;
    }

    public int getIFUNumX() {
        return _IFUNumX;
    }

    public int getIFUNumY() {
        return _IFUNumY;
    }

    public String getIFUMethod() {
        return _IFUMethod;
    }


    public double getCentralWavelength() {
        return _centralWavelength;
    }

    public double getReadNoise() {
        return _readNoiseValue;
    }

}

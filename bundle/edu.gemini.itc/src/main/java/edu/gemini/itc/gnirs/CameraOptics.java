// This software is Copyright(c) 2010 Association of Universities for
// Research in Astronomy, Inc.  This software was prepared by the
// Association of Universities for Research in Astronomy, Inc. (AURA)
// acting as operator of the Gemini Observatory under a cooperative
// agreement with the National Science Foundation. This software may 
// only be used or copied as described in the license set out in the 
// file LICENSE.TXT included with the distribution package.
//
package edu.gemini.itc.gnirs;

import edu.gemini.itc.shared.TransmissionElement;
import edu.gemini.itc.shared.Instrument;

/**
 * This is the interface for the camera optics.
 */
public interface CameraOptics  {
    
    public double getPixelScale();
}

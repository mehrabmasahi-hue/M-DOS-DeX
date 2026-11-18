package com.goldan.mdosdex

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Dedicated portrait QR capture screen for Link to M-DOS.
 * This avoids relying on an implicit library activity launch after
 * the camera permission callback.
 */
class MdosCaptureActivity : CaptureActivity()

package com.example.myapplication.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.R
import com.example.myapplication.detection.DetectionResult
import com.example.myapplication.detection.YellowBoxCrop
import com.example.myapplication.detection.YellowBoxDetector
import com.example.myapplication.detection.YellowDetectionSettings
import com.example.myapplication.ocr.JapaneseOcrProcessor
import com.example.myapplication.ocr.TitleChangeDetector
import com.example.myapplication.ocr.TitleDecision
import com.example.myapplication.translation.JapaneseKoreanTranslator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReelsOverlayCaptureService : Service() {
    private val mainHandler = Handler()
    private val detectionSettings = YellowDetectionSettings()
    private val ocrProcessor = JapaneseOcrProcessor()
    private val translator = JapaneseKoreanTranslator()

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var subtitleView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var previousNormalizedTitle: String? = null
    private var previousTranslation: String? = null
    private var processingFrame = false
    private var runningFrameLoop = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!runningFrameLoop) return
            processLatestFrame()
            captureHandler?.postDelayed(this, FRAME_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopFrameLoop()
        removeOverlay()
        releaseCapture()
        ocrProcessor.close()
        translator.close()
        super.onDestroy()
    }

    private fun startCapture(intent: Intent) {
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            updateStatus("Overlay permission missing")
            stopSelf()
            return
        }

        addOverlay()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = getResultData(intent)
        if (resultCode == 0 || resultData == null) {
            updateStatus("Screen capture consent missing")
            stopSelf()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            updateStatus("MediaProjection unavailable")
            stopSelf()
            return
        }

        captureThread = HandlerThread("screen-capture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                updateStatus("Projection stopped")
                releaseCapture()
                stopSelf()
            }
        }
        mediaProjection!!.registerCallback(projectionCallback!!, captureHandler)

        createVirtualDisplay()
        updateSubtitle("번역 자막 준비 중")
        startFrameLoop()
    }

    private fun createVirtualDisplay() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "reels-overlay-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler
        )
    }

    private fun addOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 14, 24, 14)
            background = getDrawable(R.drawable.subtitle_overlay_background)
        }

        subtitleView = TextView(this).apply {
            text = "번역 자막 테스트"
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(5f, 0f, 2f, 0xCC000000.toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt()
            maxLines = 3
            textSize = 21f
            gravity = Gravity.CENTER
            includeFontPadding = true
        }

        container.addView(subtitleView)
        container.setOnTouchListener(OverlayTouchListener())
        overlayView = container

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = resources.displayMetrics.heightPixels - 420
        }

        windowManager?.addView(overlayView, overlayParams)
    }

    private inner class OverlayTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private val gestureDetector = GestureDetector(
            this@ReelsOverlayCaptureService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(event: MotionEvent) {
                    stopSelf()
                }
            }
        )

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            gestureDetector.onTouchEvent(event)
            val params = overlayParams ?: return false
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun captureOneFrame() {
        captureHandler?.post {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                updateStatus("No frame yet")
                return@post
            }

            image.use {
                val bitmap = it.toBitmap()
                val uri = saveBitmap(bitmap)
                bitmap.recycle()
                updateStatus(
                    if (uri != null) "Saved capture" else "Save failed"
                )
            }
        }
    }

    private fun startFrameLoop() {
        if (runningFrameLoop) return
        runningFrameLoop = true
        captureHandler?.post(frameRunnable)
    }

    private fun stopFrameLoop() {
        runningFrameLoop = false
        captureHandler?.removeCallbacks(frameRunnable)
    }

    private fun processLatestFrame() {
        if (processingFrame) return
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        processingFrame = true

        image.use {
            val bitmap = it.toBitmap()
            try {
                val result = YellowBoxDetector.detectYellowBoxes(bitmap, detectionSettings)
                val crop = result.bestCrop()
                if (crop == null) {
                    recycleDetectionResult(result, keepCrop = null)
                    processingFrame = false
                    return
                }
                recycleDetectionResult(result, keepCrop = crop)
                recognizeAndTranslate(crop)
            } catch (error: Throwable) {
                updateSubtitle("자막 처리 실패")
                processingFrame = false
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun DetectionResult.bestCrop(): YellowBoxCrop? {
        return crops.maxByOrNull { crop -> crop.rect.width * crop.rect.height }
    }

    private fun recycleDetectionResult(
        result: DetectionResult,
        keepCrop: YellowBoxCrop?
    ) {
        result.debugBitmap.recycle()
        result.maskBitmap.recycle()
        result.crops.forEach { crop ->
            if (crop !== keepCrop) {
                crop.bitmap.recycle()
                if (crop.ocrBitmap !== crop.bitmap) {
                    crop.ocrBitmap.recycle()
                }
            } else {
                if (crop.ocrBitmap !== crop.bitmap) {
                    crop.bitmap.recycle()
                }
            }
        }
    }

    private fun recognizeAndTranslate(crop: YellowBoxCrop) {
        ocrProcessor.recognize(
            bitmap = crop.ocrBitmap,
            onSuccess = { text ->
                crop.ocrBitmap.recycle()
                if (text.isBlank()) {
                    processingFrame = false
                    return@recognize
                }

                val comparison = TitleChangeDetector.compare(
                    previousNormalizedText = previousNormalizedTitle,
                    currentText = text
                )

                when (comparison.decision) {
                    TitleDecision.FirstTitle,
                    TitleDecision.NewTitle -> {
                        previousNormalizedTitle = comparison.normalizedText
                        translateTitle(text)
                    }
                    TitleDecision.SameTitle -> {
                        previousTranslation?.let(::updateSubtitle)
                        processingFrame = false
                    }
                    TitleDecision.Unknown -> {
                        processingFrame = false
                    }
                }
            },
            onFailure = {
                crop.ocrBitmap.recycle()
                processingFrame = false
            }
        )
    }

    private fun translateTitle(sourceText: String) {
        translator.translate(
            text = sourceText,
            onSuccess = { translatedText ->
                if (translatedText.isNotBlank()) {
                    previousTranslation = translatedText
                    updateSubtitle(translatedText)
                }
                processingFrame = false
            },
            onFailure = {
                updateSubtitle(sourceText)
                processingFrame = false
            }
        )
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)
        val bitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        paddedBitmap.recycle()
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val name = "reels_capture_${timestamp()}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ReelsOverlayCapture")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ReelsOverlayCapture"
            )
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File(dir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        }
    }

    private fun updateStatus(message: String) {
        // Status is kept in the notification/app screen for the release overlay.
    }

    private fun updateSubtitle(message: String) {
        mainHandler.post {
            subtitleView?.text = message
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        subtitleView = null
        overlayParams = null
        windowManager = null
    }

    private fun releaseCapture() {
        projectionCallback?.let { callback ->
            mediaProjection?.unregisterCallback(callback)
        }
        projectionCallback = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }

    private fun startAsForeground() {
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Reels overlay capture")
            .setContentText("Overlay caption test is running")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Reels overlay capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    companion object {
        const val ACTION_START = "com.example.myapplication.action.START_CAPTURE"
        const val ACTION_STOP = "com.example.myapplication.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_CHANNEL_ID = "reels_overlay_capture"
        private const val NOTIFICATION_ID = 1001
        private const val FRAME_INTERVAL_MILLIS = 1_000L
    }
}

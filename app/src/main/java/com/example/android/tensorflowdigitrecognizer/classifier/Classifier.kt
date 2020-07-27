package com.example.android.tensorflowdigitrecognizer.classifier

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Classifier @Throws(IOException::class)
constructor(private val context: Context) {

    private val MODEL_PATH: String = "mnist.tflite"
    private val interpreter: Interpreter

    init {
        val assetManager = context.assets
        val model = loadModelFile(assetManager)

        val options = Interpreter.Options()
        options.setNumThreads(3)
        options.setUseNNAPI(true)

        interpreter = Interpreter(model, options)
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager) : MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(bitmap: Bitmap) : Int {
        val inputByteBuffer = preProcess(bitmap)
        val outPutArray = Array(1) {FloatArray(10)}
        interpreter.run(inputByteBuffer, outPutArray)
        return postProcess(outPutArray)
    }

    fun preProcess(bitmap: Bitmap): ByteBuffer {
        val sealedBitmap = Bitmap.createScaledBitmap(bitmap, 28, 28, false)
        return convertBitmapToByteBuffer(sealedBitmap)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 1 * 28 * 28 * 1)
        byteBuffer.order(ByteOrder.nativeOrder())

        val imagePixels = IntArray(28 * 28)
        bitmap.getPixels(imagePixels, 0, bitmap.width, 0 ,0, bitmap.width, bitmap.height)
        var pixel = 0
        for(i in 0 until 28) {
            for (j in 0 until 28) {
                val color = imagePixels[pixel++]
                byteBuffer.putFloat(convertToGreyScale(color))
            }
        }
        return byteBuffer
    }

    private fun convertToGreyScale(color: Int): Float {
        val r = (color shr 16 and 0xFF).toFloat()
        val g = (color shr 8 and 0xFF).toFloat()
        val b = (color and 0xFF).toFloat()

        val grayScaleValue = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        return grayScaleValue/255.0f
    }

    private fun postProcess(outputArray: Array<FloatArray>) : Int {
        var maxIndex = -1
        var maxProb = 0.0f
        for(i in 0 until outputArray[0].size) {
            if (outputArray[0][i] > maxProb) {
                maxProb = outputArray[0][1]
                maxIndex = i
            }
        }
        return maxIndex
    }
}
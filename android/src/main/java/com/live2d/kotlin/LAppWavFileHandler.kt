/*
 *
 *  * Copyright(c) Live2D Inc. All rights reserved.
 *  *
 *  * Use of this source code is governed by the Live2D Open Software license
 *  * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 *
 */

package com.live2d.kotlin

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.IOException

class LAppWavFileHandler(private val filePath: String) : Thread() {
    
    override fun run() {
        loadWavFile()
    }

    fun loadWavFile() {
        // 対応していないAPI(API24未満)の場合は音声再生を行わない。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }

        val mediaExtractor = MediaExtractor()
        try {
            val afd: AssetFileDescriptor = LAppDelegate.getInstance().getActivity()!!.assets.openFd(filePath)
            mediaExtractor.setDataSource(afd)
        } catch (e: IOException) {
            // 例外が発生したらエラーだけだして再生せずreturnする。
            e.printStackTrace()
            return
        }

        val mf = mediaExtractor.getTrackFormat(0)
        val samplingRate = mf.getInteger(MediaFormat.KEY_SAMPLE_RATE)

        val bufferSize = AudioTrack.getMinBufferSize(
            samplingRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(samplingRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        
        audioTrack.play()

        // ぶつぶつ音を回避
        val offset = 100
        val voiceBuffer = LAppPal.loadFileAsBytes(filePath)
        audioTrack.write(voiceBuffer, offset, voiceBuffer.size - offset)
    }
}

package org.thebottle.libOwl.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Renders report HTML in an off-screen WebView and hands it to Android's print service (printers or Save as PDF). */
object ReportPrinter {
    // Held so the WebView isn't garbage-collected before the print job has read it.
    private var webView: WebView? = null

    fun print(context: Context, html: String, jobName: String) {
        val view = WebView(context)
        var started = false
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String?) {
                if (started) return
                started = true
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(jobName, v.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
            }
        }
        webView = view
        view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}

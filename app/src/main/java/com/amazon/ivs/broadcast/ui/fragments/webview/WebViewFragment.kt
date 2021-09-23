package com.amazon.ivs.broadcast.ui.fragments.webview

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.amazon.ivs.broadcast.App
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.setVisible
import com.amazon.ivs.broadcast.common.showSnackBar
import com.amazon.ivs.broadcast.databinding.FragmentWebBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment

class WebViewFragment : BaseFragment() {

    private lateinit var binding: FragmentWebBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWebBinding.inflate(inflater, container, false)
        App.component.inject(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                showLoading()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                hideLoading()
                if (isAdded) {
                    binding.root.showSnackBar(getString(R.string.err_failed_to_load_web), ::loadUrl)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                hideLoading()
            }
        }
        loadUrl()
    }

    private fun hideLoading() {
        if (isAdded) {
            binding.webProgressBar.setVisible(false)
        }
    }

    private fun showLoading() {
        if (isAdded) {
            binding.webProgressBar.setVisible(true)
        }
    }

    fun loadUrl() {
        showLoading()
        binding.webView.loadUrl(configurationViewModel.webViewUrl)
    }
}

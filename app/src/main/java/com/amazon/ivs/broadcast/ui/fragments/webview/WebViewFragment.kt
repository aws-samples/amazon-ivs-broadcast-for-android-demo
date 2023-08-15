package com.amazon.ivs.broadcast.ui.fragments.webview

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.amazon.ivs.broadcast.R
import com.amazon.ivs.broadcast.common.setVisible
import com.amazon.ivs.broadcast.common.showSnackBar
import com.amazon.ivs.broadcast.common.viewBinding
import com.amazon.ivs.broadcast.databinding.FragmentWebBinding
import com.amazon.ivs.broadcast.ui.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WebViewFragment : BaseFragment(R.layout.fragment_web) {
    private val binding by viewBinding(FragmentWebBinding::bind)

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

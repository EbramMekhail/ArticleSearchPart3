package com.codepath.articlesearch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Headers
import org.json.JSONException

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

private const val TAG = "MainActivity/"
private const val SEARCH_API_KEY = BuildConfig.API_KEY
private const val ARTICLE_SEARCH_URL =
    "https://api.nytimes.com/svc/search/v2/articlesearch.json?api-key=$SEARCH_API_KEY"

class MainActivity : AppCompatActivity() {
    private lateinit var articlesRecyclerView: RecyclerView
    private lateinit var binding: ActivityMainBinding
    private lateinit var swipeContainer: SwipeRefreshLayout

    private val articles = mutableListOf<DisplayArticle>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            // network is available for use
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Toast.makeText(
                    context,
                    "You are online! You will be receiving the latest news!",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Network capabilities have changed for the network
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val unmetered =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }

            // lost network connection
            override fun onLost(network: Network) {
                super.onLost(network)
                Toast.makeText(
                    context,
                    "You are offline! Go back online to receive the latest news!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        articlesRecyclerView = findViewById(R.id.articles)
        // TODO: Set up ArticleAdapter with articles
        val articleAdapter = ArticleAdapter(this, articles)

        lifecycleScope.launch {
            (application as ArticleApplication).db.articleDao().getAll().collect { databaseList ->
                databaseList.map { entity ->
                    DisplayArticle(
                        entity.headline,
                        entity.articleAbstract,
                        entity.byline,
                        entity.mediaImageUrl
                    )
                }.also { mappedList ->
                    articles.clear()
                    articles.addAll(mappedList)
                    articleAdapter.notifyDataSetChanged()
                }
            }
        }

        articlesRecyclerView.adapter = articleAdapter

        articlesRecyclerView.layoutManager = LinearLayoutManager(this).also {
            val dividerItemDecoration = DividerItemDecoration(this, it.orientation)
            articlesRecyclerView.addItemDecoration(dividerItemDecoration)
        }

        swipeContainer = findViewById(R.id.swipeContainer)
        swipeContainer.setOnRefreshListener {
            getData(articleAdapter)
            swipeContainer.isRefreshing = false
        }

        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )

        getData(articleAdapter)
    }

    private fun getData(articleAdapter: ArticleAdapter) {
        val client = AsyncHttpClient()
        client.get(
            ARTICLE_SEARCH_URL,
            object : JsonHttpResponseHandler() {
                override fun onFailure(
                    statusCode: Int,
                    headers: Headers?,
                    response: String?,
                    throwable: Throwable?
                ) {
                    Log.e(TAG, "Failed to fetch articles: $statusCode")
                }

                override fun onSuccess(statusCode: Int, headers: Headers, json: JSON) {
                    Log.i(TAG, "Successfully fetched articles: $json")
                    try {
                        // TODO: Create the parsedJSON

                        // TODO: Do something with the returned json (contains article information)
                        val parsedJson = createJson().decodeFromString(
                            SearchNewsResponse.serializer(),
                            json.jsonObject.toString()
                        )

                        // TODO: Save the articles and reload the screen
                        parsedJson.response?.docs?.let { list ->
                            lifecycleScope.launch(IO) {
                                (application as ArticleApplication).db.articleDao().deleteAll()
                                (application as ArticleApplication).db.articleDao().insertAll(
                                    list.map {
                                        ArticleEntity(
                                            headline = it.headline?.main,
                                            articleAbstract = it.abstract,
                                            byline = it.byline?.original,
                                            mediaImageUrl = it.mediaImageUrl
                                        )
                                    }
                                )
                            }
                            articleAdapter.notifyDataSetChanged()
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Exception: $e")
                    }
                }
            }
        )
    }
}
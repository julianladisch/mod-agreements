package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpConfig
import groovyx.net.http.HttpObjectConfig
import java.net.HttpURLConnection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import groovy.xml.XmlSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer

@CompileStatic
public abstract class WebSourceAdapter {

  private static HttpBuilder GLOBAL_CLIENT
  
  protected HttpBuilder instanceClient = null
  protected HttpBuilder getHttpClient() {
    if (!instanceClient) {
      if (!GLOBAL_CLIENT) {
        GLOBAL_CLIENT = HttpBuilder.configure {
          execution.executor = new ThreadPoolExecutor(
            2,     // Min Idle threads.
            10,    // 10 threads max.
            10000, // 10 second keep alive
            TimeUnit.MILLISECONDS, // Makes the above wait time in 'seconds'
            new SynchronousQueue<Runnable>() // Use a synchronous queue
          )
          
          client.clientCustomizer { HttpURLConnection conn ->
            conn = (HttpURLConnection) conn // For some reason compiler treats 'conn' as an Object which fails static type checking without this cast.
            conn.connectTimeout = 5000    // 5 Seconds
            conn.readTimeout    = 900000  // 15 Mins
          }
        }
      }
      instanceClient = GLOBAL_CLIENT
    }
    
    instanceClient
  }
  
  
  WebSourceAdapter() {
    this(null)
  }
  
  WebSourceAdapter(HttpBuilder httpBuilder) {
    instanceClient = httpBuilder
  }
  
  protected final String stripTrailingSlash (final String uri) {
    uri.endsWith('//') ? uri.substring(0, uri.length() - 1) : uri
  }
  
  protected final def getAsync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getAsync( url, null, expand)
  }
  protected final CompletableFuture getAsync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    httpClient.getAsync({
      request.uri = url
      request.uri.query = params
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }
  
  protected final def getSync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getSync( url, null, expand)
  }

  protected final def getSync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    def header = "Folio mod-agreements / ${Tenants.currentId()}"
    httpClient.get({
      request.uri = url
      request.uri.query = params
      request.headers['User-Agent'] = header
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }

      // Manually swap to newer GPathResult
      response.parser('text/xml'){ ChainedHttpConfig cfg, FromServer fs ->
        return new XmlSlurper().parse(fs.inputStream)
      }
    })
  }
  
  protected final def post (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    post(url, jsonData, null, expand)
  }
  protected final def post (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    httpClient.post({
      request.uri = url
      request.uri.query = params
      request.body = jsonData
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }
  
  protected final def put (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    put(url, jsonData, null, expand)
  }
  protected def put (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    
    httpClient.put({
      request.uri = url
      request.uri.query = params
      request.body = jsonData
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }
  
  protected final def patch (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    patch(url, jsonData, null, expand)
  }
  protected final def patch (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    
    httpClient.patch({
      request.uri = url
      request.uri.query = params
      request.body = jsonData
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }
  
  protected final def delete (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    delete(url, null, expand)
  }
  protected final def delete (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    
    httpClient.delete({
      request.uri = url
      request.uri.query = params
      
      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }
}

/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.http.util

import java.io.{ File, FileInputStream, FileNotFoundException, InputStream }
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl._

import com.ning.http.client.AsyncHttpClientConfig
import io.gatling.core.util.Io._
import io.gatling.http.ssl.{ GatlingKeyManager, GatlingKeystore }

object SslHelper {

  private def storeStream(filePath: String): InputStream = {
    val storeFile = new File(filePath)
    if (storeFile.exists)
      new FileInputStream(storeFile)
    else
      Option(getClass.getClassLoader.getResourceAsStream(filePath)).getOrElse(throw new FileNotFoundException(filePath))
  }

  def newTrustManagers(storeType: Option[String], file: String, password: String, algorithm: Option[String]): Array[TrustManager] = {

    withCloseable(storeStream(file)) { is =>
      val trustStore = KeyStore.getInstance(storeType.getOrElse(KeyStore.getDefaultType))
      trustStore.load(is, password.toCharArray)
      val algo = algorithm.getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      val tmf = TrustManagerFactory.getInstance(algo)
      tmf.init(trustStore)
      tmf.getTrustManagers
    }
  }

  def newKeyManagers(storeType: Option[String], file: String, password: String, algorithm: Option[String]): Array[KeyManager] = {

    withCloseable(storeStream(file)) { is =>
      val charPassword = password.toCharArray
      val keyStore = GatlingKeystore.getLoadedInstance(is, charPassword, storeType.getOrElse(KeyStore.getDefaultType))
      val algo = algorithm.getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      val kmf = KeyManagerFactory.getInstance(algo)
      kmf.init(null, charPassword)
      kmf.getKeyManagers.map(km => new GatlingKeyManager(km.asInstanceOf[X509KeyManager], keyStore))
    }
  }

  implicit class RichAsyncHttpClientConfigBuilder(val ahcConfigBuilder: AsyncHttpClientConfig.Builder) extends AnyVal {

    def setSSLContext(trustManagers: Option[Array[TrustManager]], keyManagers: Option[Array[KeyManager]]): AsyncHttpClientConfig.Builder = {
      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagers.orNull, trustManagers.orNull, new SecureRandom)
      ahcConfigBuilder.setSSLContext(sslContext)
    }
  }
}
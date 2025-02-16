package za.co.britehouse.flutter_microsoft_authentication

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FlutterMicrosoftAuthenticationPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {

  private lateinit var channel : MethodChannel
  private lateinit var activity: Activity
  private lateinit var context: Context
  private var binding: FlutterPluginBinding? = null
  private var mSingleAccountApp: ISingleAccountPublicClientApplication? = null

  companion object {
    private const val TAG = "FMAuthPlugin"
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    binding = flutterPluginBinding;

    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_microsoft_authentication")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val scopesArg : ArrayList<String> = call.argument("scopes")!!
    val scopes: Array<String> = scopesArg?.toTypedArray()!!
    val authority: String = call.argument("authority")!!
    val configPath: String = call.argument("configPath")!!

    when(call.method){
      "acquireTokenInteractively" -> acquireTokenInteractively(scopes, authority, result)
      "acquireTokenSilently" -> acquireTokenSilently(scopes, authority, result)
      "loadAccount" -> loadAccount(result)
      "signOut" -> signOut(result)
      "init" -> initPlugin(configPath)
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    this.binding = null;

  }

  override fun onDetachedFromActivity() {
    channel.setMethodCallHandler(null)
    this.binding = null;
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity;
  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  @Throws(IOException::class)
  private fun getConfigFile(path: String): File {
    val key: String = binding?.getFlutterAssets()?.getAssetFilePathBySubpath(path)!!
    val configFile = File(activity.applicationContext.cacheDir, "config.json")

    try {
      val assetManager = context.assets

      val inputStream = assetManager.open(key)
      val outputStream = FileOutputStream(configFile)
      try {
        Log.d(TAG, "File exists: ${configFile.exists()}")
        if (configFile.exists()) {
          outputStream.write("".toByteArray())
        }
        inputStream.copyTo(outputStream)
      } finally {
        inputStream.close()
        outputStream.close()
      }
      return  configFile

    } catch (e: IOException) {
      throw IOException("Could not open config file", e)
    }
  }

  private fun initPlugin(assetPath: String) {
    createSingleAccountPublicClientApplication(assetPath)
  }

  private fun createSingleAccountPublicClientApplication(assetPath: String) {
    val configFile = getConfigFile(assetPath)
    val context: Context = activity.applicationContext

    PublicClientApplication.createSingleAccountPublicClientApplication(
      context,
      configFile,
      object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
        override fun onCreated(application: ISingleAccountPublicClientApplication) {
          /**
           * This test app assumes that the app is only going to support one account.
           * This requires "account_mode" : "SINGLE" in the config json file.
           *
           */
          Log.d(TAG, "INITIALIZED")
          mSingleAccountApp = application
        }

        override fun onError(exception: MsalException) {
          Log.e(TAG, exception.message!!)
        }
      })
  }

  private fun acquireTokenInteractively(scopes: Array<String>, authority: String, result: Result) {
    if (mSingleAccountApp == null) {
      result.error("MsalClientException", "Account not initialized", null)
    }

    return mSingleAccountApp!!.signIn(activity, "", scopes, getAuthInteractiveCallback(result))
  }

  private fun acquireTokenSilently(scopes: Array<String>, authority: String, result: Result) {
    if (mSingleAccountApp == null) {
      result.error("MsalClientException", "Account not initialized", null)
    }

    return mSingleAccountApp!!.acquireTokenSilentAsync(scopes, authority, getAuthSilentCallback(result))
  }

  private fun signOut(result: Result){
    if (mSingleAccountApp == null) {
      result.error("MsalClientException", "Account not initialized", null)
    }

    return mSingleAccountApp!!.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
      override fun onSignOut() {
        result.success("SUCCESS")
      }

      override fun onError(exception: MsalException) {
        Log.e(TAG, exception.message!!)
        result.error("ERROR", exception.errorCode, null)
      }
    })

  }

  private fun getAuthInteractiveCallback(result: Result): AuthenticationCallback {

    return object : AuthenticationCallback {

      override fun onSuccess(authenticationResult: IAuthenticationResult) {
        /* Successfully got a token, use it to call a protected resource - MSGraph */
        Log.d(TAG, "Successfully authenticated")
        Log.d(TAG, "ID Token: " + authenticationResult.account.claims!!["id_token"])
        val accessToken = authenticationResult.accessToken
        result.success(accessToken)
      }

      override fun onError(exception: MsalException) {
        /* Failed to acquireToken */

        Log.d(TAG, "Authentication failed: ${exception.errorCode}")

        if (exception is MsalClientException) {
          /* Exception inside MSAL, more info inside MsalError.java */
          Log.d(TAG, "Authentication failed: MsalClientException")
          result.error("MsalClientException", exception.errorCode, null)

        } else if (exception is MsalServiceException) {
          /* Exception when communicating with the STS, likely config issue */
          Log.d(TAG, "Authentication failed: MsalServiceException")
          result.error("MsalServiceException", exception.errorCode, null)
        }
      }

      override fun onCancel() {
        /* User canceled the authentication */
        Log.d(TAG, "User cancelled login.")
        result.error("MsalUserCancel", "User cancelled login.", null)
      }
    }
  }

  private fun getAuthSilentCallback(result: Result): AuthenticationCallback {
    return object : AuthenticationCallback {

      override fun onSuccess(authenticationResult: IAuthenticationResult) {
        Log.d(TAG, "Successfully authenticated")
        val accessToken = authenticationResult.accessToken
        result.success(accessToken)
      }

      override fun onError(exception: MsalException) {
        /* Failed to acquireToken */
        Log.d(TAG, "Authentication failed: ${exception.message}")

        when (exception) {
          is MsalClientException -> {
            /* Exception inside MSAL, more info inside MsalError.java */
            result.error("MsalClientException", exception.message, null)
          }
          is MsalServiceException -> {
            /* Exception when communicating with the STS, likely config issue */
            result.error("MsalServiceException", exception.message, null)
          }
          is MsalUiRequiredException -> {
            /* Tokens expired or no session, retry with interactive */
            result.error("MsalUiRequiredException", exception.message, null)
          }
        }
      }

      override fun onCancel() {
        /* User cancelled the authentication */
        Log.d(TAG, "User cancelled login.")
        result.error("MsalUserCancel", "User cancelled login.", null)
      }
    }
  }

  private fun loadAccount(result: Result) {
    if (mSingleAccountApp == null) {
      result.error("MsalClientException", "Account not initialized", null)
    }

    return mSingleAccountApp!!.getCurrentAccountAsync(object :
      ISingleAccountPublicClientApplication.CurrentAccountCallback {
      override fun onAccountLoaded(activeAccount: IAccount?) {
        if (activeAccount != null) {
          result.success(activeAccount.username)
        }
      }

      override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
        if (currentAccount == null) {
          // Perform a cleanup task as the signed-in account changed.
          Log.d(TAG, "No Account")
          result.success(null)
        }
      }

      override fun onError(exception: MsalException) {
        Log.e(TAG, exception.message!!)
        result.error("MsalException", exception.message, null)
      }
    })
  }

}

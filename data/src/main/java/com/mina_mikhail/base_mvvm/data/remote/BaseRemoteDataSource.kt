package com.mina_mikhail.base_mvvm.data.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.mina_mikhail.base_mvvm.domain.utils.BaseResponse
import com.mina_mikhail.base_mvvm.domain.utils.ErrorResponse
import com.mina_mikhail.base_mvvm.domain.utils.FailureStatus.API_FAIL
import com.mina_mikhail.base_mvvm.domain.utils.FailureStatus.NO_INTERNET
import com.mina_mikhail.base_mvvm.domain.utils.FailureStatus.OTHER
import com.mina_mikhail.base_mvvm.domain.utils.FailureStatus.SERVER_SIDE_EXCEPTION
import com.mina_mikhail.base_mvvm.domain.utils.FailureStatus.TOKEN_EXPIRED
import com.mina_mikhail.base_mvvm.domain.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject

open class BaseRemoteDataSource @Inject constructor() {

  suspend fun <T> safeApiCall(apiCall: suspend () -> T): Resource<T> {
    return withContext(Dispatchers.IO) {
      try {
        val apiResponse: T = apiCall.invoke()

        if ((apiResponse as BaseResponse<*>).validationErrors != null
          && (apiResponse as BaseResponse<*>).validationErrors?.isNotEmpty() == true
        ) {
          var validationErrors = ""
          (apiResponse as BaseResponse<*>).validationErrors?.forEach {
            validationErrors += "${it.errorMessage} \n"
          }

          Resource.Failure(API_FAIL, 200, validationErrors)
        } else if ((apiResponse as BaseResponse<*>).result == null) {
          Resource.Empty
        } else if ((apiResponse as BaseResponse<*>).result is List<*>) {
          if ((apiResponse.result as List<*>).isNotEmpty()) {
            Resource.Success(apiResponse)
          } else {
            Resource.Empty
          }
        } else if ((apiResponse as BaseResponse<*>).result is Boolean) {
          if ((apiResponse.result as Boolean)) {
            Resource.Success(apiResponse)
          } else {
            Resource.Failure(API_FAIL, 200, apiResponse.detail)
          }
        } else {
          Resource.Success(apiResponse)
        }
      } catch (throwable: Throwable) {
        when (throwable) {
          is HttpException -> {
            when {
              throwable.code() == 422 -> {
                val jObjError = JSONObject(throwable.response()!!.errorBody()!!.string())
                val apiResponse = jObjError.toString()
                val response = Gson().fromJson(apiResponse, BaseResponse::class.java)

                Resource.Failure(API_FAIL, throwable.code(), response.detail)
              }
              throwable.code() == 401 -> {
                val errorResponse = Gson().fromJson(
                  throwable.response()?.errorBody()!!.charStream().readText(),
                  ErrorResponse::class.java
                )

                Resource.Failure(TOKEN_EXPIRED, throwable.code(), errorResponse.detail)
              }
              else -> {
                try {
                  val errorResponse = Gson().fromJson(
                    throwable.response()?.errorBody()!!.charStream().readText(),
                    ErrorResponse::class.java
                  )

                  Resource.Failure(SERVER_SIDE_EXCEPTION, throwable.code(), errorResponse.detail)
                } catch (ex: JsonSyntaxException) {
                  Resource.Failure(SERVER_SIDE_EXCEPTION, throwable.code(), null)
                }
              }
            }
          }

          is UnknownHostException -> {
            Resource.Failure(NO_INTERNET, null, null)
          }

          is ConnectException -> {
            Resource.Failure(NO_INTERNET, null, null)
          }

          else -> {
            Resource.Failure(OTHER, null, null)
          }
        }
      }
    }
  }
}
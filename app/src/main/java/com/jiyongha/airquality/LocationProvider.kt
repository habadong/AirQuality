package com.jiyongha.airquality

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.newFixedThreadPoolContext
import java.lang.Exception

class LocationProvider(val context : Context) { // LocationProvider 에서 context가 없기 때문에 인수를 건내 줘야 사용
    private var location : Location? = null // 위도와 경도
    private var locationManager : LocationManager? = null // 시스템 위치서비스에 접근하는 클래스

    init { // LocationProvider가 호출되면 제일 먼저 실행되는 초기화 함수
        getLocation()
    }

    private fun getLocation() : Location? {
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var gpsLocation : Location? = null
            var networkLocation : Location? = null

            // GPS or Network 가 활성화 되었는지 확인

            // locationManager!! 한 이유는 nullable 이면서 만약에 null로 오게 되면 try catch에서 exception 처리되어 나오게 됨
            val isGPSEnabled = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGPSEnabled && !isNetworkEnabled) {
                return null
            } else {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return null
                }
                if (isNetworkEnabled) {
                    networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                if (isGPSEnabled) {
                    gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if (gpsLocation != null && networkLocation != null) {
                    if (gpsLocation.accuracy > networkLocation.accuracy) {
                        location = gpsLocation
                    } else {
                        location = networkLocation
                    }
                } else {
                    if (gpsLocation != null) {
                        location = gpsLocation
                    }
                    if (networkLocation != null) {
                        location = networkLocation
                    }
                }
            }

        } catch (e : Exception) {
            e.printStackTrace()
        }

        return location
    }

//    fun getLocationLatitude() : Double {    // 위도
//        return location?.latitude ?: 0.0  // location 이 Null 이면(latitude가 null일 경우) 0.0을 반환한다   ?:
//    }
//
//    fun getLocationLongitude() : Double {   // 경도
//        return location?.longitude ?: 0.0
//    }
    fun getLocationLatitude() : Double? {    // 위도
        return location?.latitude
    }

    fun getLocationLongitude() : Double? {   // 경도
        return location?.longitude
    }
}
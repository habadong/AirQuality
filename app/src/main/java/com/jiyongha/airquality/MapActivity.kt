package com.jiyongha.airquality

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.jiyongha.airquality.databinding.ActivityMainBinding
import com.jiyongha.airquality.databinding.ActivityMapBinding

// OnMapReadyCallback는 지도를 보여줄 준비가 되면 엑티비티 실행 해주는 오버라이딩 콜백
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    lateinit var binding : ActivityMapBinding

    private var mMap : GoogleMap? = null
    var currentLat : Double = 0.0
    var currentLng : Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentLat = intent.getDoubleExtra("currentLat", 0.0)
        currentLng = intent.getDoubleExtra("currentLng", 0.0)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        // this를 할 수 있는건  OnMapReadyCallback 인터페이스 구현을 했기때문
        // getMapAsync은 지도가 준비되면 this 콜백이 실행될수 있게끔 비동기적으로 실행해주는 녀석 -> 준비가 되면 콜백함수인 onMapReady 실행
        mapFragment?.getMapAsync(this)

        setButton()
    }

    private fun setButton() {
        binding.btnCheckHere.setOnClickListener {
            mMap?.let {
                val intent = Intent() // 데이터를 넣기위한 인텐트임 , 백스텍에 이미 main이 존재하기 때문
                intent.putExtra("latitude", it.cameraPosition.target.latitude)
                intent.putExtra("longitude", it.cameraPosition.target.longitude)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

        binding.fabCurrentLocation.setOnClickListener {
            val locationProvider = LocationProvider(this@MapActivity)
            val latitude = locationProvider.getLocationLatitude()
            val longitude = locationProvider.getLocationLongitude()

            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude!!, longitude!!), 16f))
            setMarker()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.let {
            val currentLocation = LatLng(currentLat, currentLng)
            it.setMaxZoomPreference(20.0f)
            it.setMinZoomPreference(12.0f)
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
            setMarker()
        }
    }

    private fun setMarker() {
        // mMap이 널이 아닌경우에만 실행되는 let 문
        mMap?.let {
            it.clear()
            var markerOption = MarkerOptions()
            markerOption.position(it.cameraPosition.target)
            markerOption.title("마커 위치")
            val marker = it.addMarker(markerOption) //addMarker는 마커는 반환함. marker 객체를 생성하고 계속 추적하는 이유는 카메라 이동이 되었을때 마커도 같이 변경이 되게 만들어야하기때문임

            it.setOnCameraMoveListener { // 줌, 줌아웃, 카메라 이동시 감지
                marker?.let { marker ->
                    marker.position = it.cameraPosition.target
                }

            }
        }
    }
}
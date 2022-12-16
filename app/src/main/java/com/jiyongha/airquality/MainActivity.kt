package com.jiyongha.airquality

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.gson.GsonBuilder
import com.jiyongha.airquality.databinding.ActivityMainBinding
import com.jiyongha.airquality.retrofit.AirQualityResponse
import com.jiyongha.airquality.retrofit.AirQualityService
import com.jiyongha.airquality.retrofit.RetrofitConnection
import com.jiyongha.airquality.viewmodel.AirQualityViewModel
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.lang.IllegalArgumentException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var binding : ActivityMainBinding
    lateinit var locationProvider: LocationProvider

    private val PERMISSIONS_REQUEST_CODE = 100

    var latitude : Double? = 0.0
    var longitude : Double? = 0.0

    // permission 목록
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    /**
     * 맵 엑티비티가 시작되고(행동을 취하고) 메인으로 돌아오면 startMapActivityResult이 실행된다
     * resultCode가 RESULT_OK 이면 data를 까보게 되는데
     * 위도와 경도 정보를 조회하고 만들었던 클래스 객체 변수에 넣어주게 된다
     * 맵 엑티비티로 부터 데이터가 오면 클래스 객체 변수에 넣어준다
     */
    var startMapActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
        object : ActivityResultCallback<ActivityResult> {
            override fun onActivityResult(result: ActivityResult?) {
                if(result?.resultCode?: 0 == Activity.RESULT_OK) {
                    latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                    longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                    updateUI()
                }
            }
        }
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
        setRefreshButton()

        setFab()

    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity) //초기화

        if (latitude == 0.0 && longitude == 0.0) {
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }

        if (latitude != null && longitude != null) {
            // (geo)지오 코딩 시작 -> 주소나 지명을 위도나 경도로 표현하던가, 위도와 경도로 주소를 표시하는 코딩을 지칭함
            // 1. 현재 위치가져오고 UI 업데이트
            val address = getCurrentAddress(latitude!!, longitude!!)

            address?.let { // address가 null 이 아닌경우에 코드 블럭 실행
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            // 2. 미세먼지 농도 가져오고 UI 업데이트

            getAirQualityData(latitude!!, longitude!!)

        } else {
            Toast.makeText(this, "위도, 경도 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
            Log.d("ui-test", "${latitude}, ${longitude}")
        }
    }

    private fun getAirQualityData(latitude: Double, longitude: Double) {
        var retrofitAPI = RetrofitConnection.getInstance().create(
            AirQualityService::class.java
        )

        retrofitAPI.getAirQualityData(
            latitude.toString(),
            longitude.toString(),
            "94de26ce-6bdf-4aad-b5e1-4ed42d4dd159"
        ).enqueue( object : Callback<AirQualityResponse> {
            override fun onResponse(
                call: Call<AirQualityResponse>,
                response: Response<AirQualityResponse>
            ) {
                if(response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "최신 데이터 업데이트 완료!", Toast.LENGTH_LONG).show()
                    response.body()?.let { updateAirUI(it) }
                } else {
                    Toast.makeText(this@MainActivity, "데이터를 가져오는 데 실패했습니다", Toast.LENGTH_LONG).show()
                    Log.d("api-test", "${response.code()}")
                }

            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this@MainActivity, "데이터를 가져오는 데 실패했습니다", Toast.LENGTH_LONG).show()
                Log.d("api-test", "${t}")
            }
        }

        )

        // execute() 동기 실행
        // enqueue() 비동기 실행 -> 메인 스레드에서 요청해도 백그라운드 스레드에서 실행함. 때문에 enqueue 안에 콜백함수를 등록하는 식
    }

    private fun updateAirUI(airQualityData: AirQualityResponse) {
        val pollutionData = airQualityData.data.current.pollution

        // 수치를 지정
        binding.tvCount.text = pollutionData.aqius.toString()

        //측정된 날짜
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()  // 한국시간 기준으로 보여주는데 원하는 방식으로 설정
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        when(pollutionData.aqius) {
            in 0..50 -> {
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }
            in 51..150 -> {
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }
            in 151..200 -> {
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }
            else -> {
                binding.tvTitle.text = "매우 나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }

    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun setFab() {
        binding.fab.setOnClickListener{
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("currentLat", latitude)
            intent.putExtra("currentLng", longitude)
            startMapActivityResult.launch(intent)
        }
    }

    private fun getCurrentAddress (latitude : Double, longitude : Double) : Address? {
        val geoCoder = Geocoder(this, Locale.KOREA) // 지오코더 객체 생성
        val addresses : List<Address>

        addresses = try {
            geoCoder.getFromLocation(latitude, longitude, 7) // maxResults 여러 지명을 줌
        } catch (ioException : IOException) {
            Toast.makeText(this, "geo코터 서비스를 이용불가 합니다.", Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException : IllegalArgumentException) {
            Toast.makeText(this, "잘못된 위도, 경도 입니다.", Toast.LENGTH_LONG).show()
            return null
        }

        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }

        return addresses[0]
    }


    private fun checkAllPermissions() {
        if(!isLocationServicesAvailable()) {
            showDialogForLocationServiceSetting()
        } else {
            isRuntimePermissionsGranted()
        }
    }

    private fun isLocationServicesAvailable() : Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager // getSystemService 가져와서 LocationManager 로 타입캐스팅을 해줌

        // locationProvider 는 gps나 네트워크를 통해 provider로 설정할수있음 -> 둘 중하나가 연결되어 있다면 서비스를 사용할 수 있다고 true를 반환하는 것임
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    private fun isRuntimePermissionsGranted() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) // coarse 보다 좀더 정교한 위치
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) //

        if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    // 결과값을 엑티비티의 오버라이딩 함으로써 구현
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == PERMISSIONS_REQUEST_CODE || grantResults.size == REQUIRED_PERMISSIONS.size) { // 내가 보낸 request가 맞다면 실행
            var checkResult = true

            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }
            if(checkResult) {
                // 위치값을 가져올 수 있음
                updateUI()
            } else { // 권한을 하나라도 허용을 안했다면 토스트 보여줌
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun showDialogForLocationServiceSetting() {  // 런처를 통해서, A -> B 로 넘어간 다음 . B -> A 로 데이터를 보내줘야 하는 상황. 그때사용하는게 ActivityResultLauncher
        getGPSPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){ result ->
            if (isLocationServicesAvailable()) {
                isRuntimePermissionsGranted()
            } else {
                Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        val builder : AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true) // 밖을 터치하여 캔슬할수 있
        builder.setPositiveButton("설정", DialogInterface.OnClickListener{ dialogInterface, i ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) // 인텐트로 안드로이드 기기 설정앱의 gps 설정 페이지로 이동하게 함
            getGPSPermissionLauncher.launch(callGPSSettingIntent)

        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener { dialogInterface, i ->
            dialogInterface.cancel()
            Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
        })
        builder.create().show()
    }
}

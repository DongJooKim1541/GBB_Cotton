package com.example.cotton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.loader.content.CursorLoader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.example.cotton.Utils.ApiService;
import com.example.cotton.Utils.BaseUrlInterface;
import com.example.cotton.Utils.RetrofitClientJson;
import com.example.cotton.ValueObject.CreateWallet.CreateWalletResultVO;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MemberInitActivity extends AppCompatActivity {

    private static final String TAG = "MemberInit Activity";

    String profileLink;
    private static final String TAG_TEXT = "text";
    Uri selectedImageUri;
    ImageButton profileImg;
    String walletAdress;
    private VideoView videoHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_init);

        //파일 접근권한 부여
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "권한 설정 완료");
        } else {
            Log.d(TAG, "권한 설정 요청");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        findViewById(R.id.checkButton).setOnClickListener(onClickListener);
        findViewById(R.id.profileImg).setOnClickListener(onClickListener);
        profileImg = findViewById(R.id.profileImg);

        startVideo();
    }

    //회원정보 입력 버튼 온클릭
    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()){
                case R.id.checkButton:
                    //파베에서 유저정보 가져옴
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                    ApiService call = RetrofitClientJson.getApiService(BaseUrlInterface.LUNIVERSE);

                    HashMap<String, String> bodyMap = new HashMap<String, String>();
                    bodyMap.put("walletType", "LUNIVERSE"); //지갑타입:루니버스
                    bodyMap.put("userKey", user.getUid()); //userKey를 파베 사용자의 UID를 가져와서 사용

                    call.listRepos(bodyMap).enqueue(new Callback<CreateWalletResultVO>() {
                        @Override
                        public void onResponse(Call<CreateWalletResultVO> call, Response<CreateWalletResultVO> response) {
                            Log.d("성공 : ", "result : " + response.body().getResult());
                            Log.d("성공 : ", "address : " + response.body().getDataCreateWallet().getAddress());
                            walletAdress = response.body().getDataCreateWallet().getAddress();
                            localUpoad();
                            startToast("저장중입니다.");
                        }

                        @Override
                        public void onFailure(Call<CreateWalletResultVO> call, Throwable t) {
                            Log.d("실패 : ", t.toString());
                        }
                    });
                    break;
                case R.id.profileImg:
                    showGallery();
                    break;
                default:
            }
        }
    };

    private void showGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent. setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, 200);
    }

    public String getPath(Uri uri){
        String[]proj = {MediaStore.Images.Media.DATA};
        CursorLoader cursorLoader = new CursorLoader(this,uri,proj,null,null,null);

        Cursor cursor = cursorLoader.loadInBackground();
        int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

        cursor.moveToFirst();

        return cursor.getString(index);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            profileImg.setImageURI(selectedImageUri);
        }
    }
    private void localUpoad() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        String name = ((EditText)findViewById(R.id.nameEditText)).getText().toString();
        String phoneNumber = ((EditText)findViewById(R.id.phoneNumberEditText)).getText().toString();
        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        Uri file = Uri.fromFile(new File(getPath(selectedImageUri)));
        final StorageReference riversRef = storageRef.child("users/" + user.getUid() + "/" + "Profile Image");
        UploadTask uploadTask = riversRef.putFile(file);

        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                // Continue with the task to get the download URL
                return riversRef.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    profileLink = downloadUri.toString();
                    FirebaseFunction firebaseFunction = new FirebaseFunction();
                    firebaseFunction.getMyDeviceToken((token)->{
                        firebaseFunction.profileUpdate(name, phoneNumber, walletAdress, 0, profileLink, token);
                        startMainActivity();
                        finish();
                        return null;
                    });
                }
            }
        });
    }


    //메인 엑티비티로 이동 함수
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void startToast(String msg) {
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

    private void jump() {
        if (isFinishing())
            return;
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void startVideo() {
        try {
            videoHolder = (VideoView)findViewById(R.id.login_video_view);
            Uri video = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.login_bg_video);
            videoHolder.setVideoURI(video);

            videoHolder.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
                    float screenRatio = videoHolder.getWidth() / (float)
                            videoHolder.getHeight();
                    float scaleX = videoRatio / screenRatio;
                    if (scaleX >= 1f) {
                        videoHolder.setScaleX(scaleX);
                    } else {
                        videoHolder.setScaleY(1f / scaleX);
                    }
                }
            });

            videoHolder.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    videoHolder.start();
                }
            });
            videoHolder.start();
        } catch (Exception ex) {
            jump();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startVideo();

    }

}
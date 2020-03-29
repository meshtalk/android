package tech.lerk.meshtalk.ui.qr;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.providers.ContactProvider;

public class QRCodeScanActivity extends AppCompatActivity {

    private static final String TAG = QRCodeScanActivity.class.getCanonicalName();
    private static final int REQUEST_CAMERA_PERMISSION = 42;
    private QRScanView scannerView;
    private final Gson gson = Utils.getGson();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
        scannerView = new QRScanView(this);
        setContentView(scannerView);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.error_no_camera_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        scannerView.setResultHandler(new QRCodeResultHandler(gson, scannerView, this));
        scannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        scannerView.stopCamera();
    }

    private static class QRCodeResultHandler implements ZXingScannerView.ResultHandler {

        private final Gson gson;
        private final QRScanView scannerView;
        private final QRCodeScanActivity activity;

        QRCodeResultHandler(Gson gson, QRScanView scannerView, QRCodeScanActivity activity) {
            this.gson = gson;
            this.scannerView = scannerView;
            this.activity = activity;
        }

        @Override
        public void handleResult(Result r) {
            Contact contact = gson.fromJson(r.getText(), Contact.class);
            if (contact != null) {
                String messagePre = activity.getString(R.string.dialog_new_contact_qr_add_confirmation_pre);
                String messagePost = activity.getString(R.string.dialog_new_contact_qr_add_confirmation_post);
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.dialog_new_contact_title)
                        .setMessage(messagePre + contact.getName() + messagePost)
                        .setNegativeButton(R.string.action_cancel, (d, w) -> {
                            d.dismiss();
                            scannerView.resumeCameraPreview(QRCodeResultHandler.this);
                        })
                        .setPositiveButton(R.string.action_add, (d, w) -> {
                            ContactProvider.get(activity).save(contact);
                            Toast.makeText(activity, R.string.success_decoding_contact, Toast.LENGTH_LONG).show();
                            activity.finish();
                        }).create().show();
            } else {
                Toast.makeText(activity, R.string.error_decoding_contact, Toast.LENGTH_LONG).show();
            }
        }
    }
}

package tech.lerk.meshtalk.ui.qr;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.util.DisplayMetrics;

import com.google.zxing.BarcodeFormat;

import java.util.Collections;

import me.dm7.barcodescanner.core.IViewFinder;
import me.dm7.barcodescanner.core.ViewFinderView;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QRScanView extends ZXingScannerView {

    public QRScanView(Context context) {
        super(context);
        setFormats(Collections.singletonList(BarcodeFormat.QR_CODE));
    }

    @Override
    protected IViewFinder createViewFinderView(Context context) {
        return new QrViewFinder(context);
    }

    static class QrViewFinder extends ViewFinderView {

        public QrViewFinder(Context context) {
            super(context);
            setSquareViewFinder(true);
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float width = displayMetrics.widthPixels * 0.625f;
            setBorderLineLength((int) width);
        }

        @Override
        public void drawLaser(Canvas canvas) {
            // æugh
        }
    }
}

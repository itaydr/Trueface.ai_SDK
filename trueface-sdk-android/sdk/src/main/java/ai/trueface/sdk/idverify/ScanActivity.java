package ai.trueface.sdk.idverify;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.acuant.mobilesdk.AcuantAndroidMobileSDKController;
import com.acuant.mobilesdk.AcuantErrorListener;
import com.acuant.mobilesdk.Card;
import com.acuant.mobilesdk.CardCroppingListener;
import com.acuant.mobilesdk.CardType;
import com.acuant.mobilesdk.DriversLicenseCard;
import com.acuant.mobilesdk.ErrorType;
import com.acuant.mobilesdk.FacialData;
import com.acuant.mobilesdk.FacialRecognitionListener;
import com.acuant.mobilesdk.LicenseDetails;
import com.acuant.mobilesdk.MedicalCard;
import com.acuant.mobilesdk.PassportCard;
import com.acuant.mobilesdk.Permission;
import com.acuant.mobilesdk.ProcessImageRequestOptions;
import com.acuant.mobilesdk.Region;
import com.acuant.mobilesdk.WebServiceListener;
import com.acuant.mobilesdk.util.Utils;

import org.jetbrains.annotations.NotNull;

import ai.trueface.sdk.R;
import ai.trueface.sdk.idverify.ScanModel.State;
import ai.trueface.sdk.idverify.util.ConfirmationListener;
import ai.trueface.sdk.idverify.util.DataContext;
import ai.trueface.sdk.idverify.util.TempImageStore;
import ai.trueface.sdk.idverify.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ScanActivity extends Activity implements WebServiceListener, CardCroppingListener, AcuantErrorListener, FacialRecognitionListener {

    private static final String TAG = ScanActivity.class.getName();
    private final String IS_SHOWING_DIALOG_KEY = "isShowingDialog";
    private final String IS_PROCESSING_DIALOG_KEY = "isProcessing";
    private final String IS_CROPPING_DIALOG_KEY = "isCropping";
    private final String IS_VALIDATING_DIALOG_KEY = "isValidating";
    private final String IS_ACTIVATING_DIALOG_KEY = "isActivating";
    private final String IS_SHOWDUPLEXDIALOG_DIALOG_KEY = "isShowDuplexDialog";
    public String sPdf417String = "";
    AcuantAndroidMobileSDKController acuantAndroidMobileSdkControllerInstance = null;
    public ImageView frontImageView;
    public ImageView backImageView;
    private TextView txtTapToCaptureFront;
    private TextView txtTapToCaptureBack;
    public Button processCardButton;
    public Button doneButton;
    private Button buttonMedical;
    private Button buttonDL;
    private Button buttonPassport;
    private RelativeLayout layoutFrontImage;
    private RelativeLayout layoutBackImage;
    private LinearLayout layoutCards;
    private TextView textViewCardInfo;
    public ScanModel scanModel = null;
    private ProgressDialog progressDialog;
    private AlertDialog showDuplexAlertDialog;
    private AlertDialog alertDialog;
    private boolean isShowErrorAlertDialog;
    private boolean isProcessing;
    private boolean isValidating;
    private boolean isActivating;
    private boolean isCropping;
    private boolean isBackSide;
    private boolean isShowDuplexDialog;
    private boolean isProcessingFacial;
    private boolean isFacialFlow;
    private ScanActivity scanActivity;
    private int cardRegion;
    private Bitmap originalImage;
    private Card processedCardInformation;
    private FacialData processedFacialData;
    private boolean isFirstLoad = true;

    /**
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onCreate(Bundle savedInstanceState)");
        }

        // load the model
        if (savedInstanceState == null) {
            if (Util.LOG_ENABLED) {
                Utils.appendLog(TAG, "if (savedInstanceState == null)");
            }
            scanModel = new ScanModel();
        } else {
            if (Util.LOG_ENABLED) {
                Utils.appendLog(TAG, "if (savedInstanceState != null)");
            }
            scanModel = DataContext.getInstance().getScanModel();
            // if coming from background and kill the app, restart the model
            if (scanModel == null) {
                scanModel = new ScanModel();
            }
        }
        DataContext.getInstance().setContext(getApplicationContext());
        setContentView(R.layout.activity_scan);

        layoutCards = findViewById(R.id.cardImagesLayout);
        layoutBackImage = findViewById(R.id.relativeLayoutBackImage);
        layoutFrontImage = findViewById(R.id.relativeLayoutFrontImage);

        frontImageView = findViewById(R.id.frontImageView);
        backImageView = findViewById(R.id.backImageView);

        textViewCardInfo = findViewById(R.id.textViewCardInfo);

        buttonMedical = findViewById(R.id.buttonMedical);
        buttonDL = findViewById(R.id.buttonDriver);
        buttonPassport = findViewById(R.id.buttonPassport);

        txtTapToCaptureFront = findViewById(R.id.txtTapToCaptureFront);
        txtTapToCaptureBack = findViewById(R.id.txtTapToCaptureBack);

        processCardButton = findViewById(R.id.processCardButton);
        processCardButton.setVisibility(View.INVISIBLE);

        doneButton = findViewById(R.id.done_activity);
        doneButton.setVisibility(View.INVISIBLE);

        initializeSDK();
        // update the UI from the model
        updateUI();
        if (Utils.LOG_ENABLED) {
            Utils.appendLog(TAG, "getScreenOrientation()=" + Util.getScreenOrientation(this));
        }
    }

    private void initializeSDK() {
        String licenseKey = "";//Set license key here
        // load the controller instance
        Util.lockScreen(this);

        if (buttonPassport != null) {
            buttonPassport.setText(R.string.passport);
        }
        if (buttonMedical != null) {
            buttonMedical.setVisibility(View.VISIBLE);
        }

        if (buttonDL != null) {
            buttonDL.setVisibility(View.VISIBLE);
        }
        if (textViewCardInfo != null) {
            textViewCardInfo.setVisibility(View.VISIBLE);
        }
        //acuantAndroidMobileSdkControllerInstance = AcuantAndroidMobileSDKController.getInstance(this,"cssnwebservicestest.com",licenseKey);
        //acuantAndroidMobileSdkControllerInstance = AcuantAndroidMobileSDKController.getInstance(this,"192.168.1.62",licenseKey);

        acuantAndroidMobileSdkControllerInstance = AcuantAndroidMobileSDKController.getInstance(this, licenseKey);


        Util.lockScreen(this);
        if (!Util.isTablet(this)) {
            acuantAndroidMobileSdkControllerInstance.setPdf417BarcodeImageDrawable(getResources().getDrawable(R.drawable.barcode));
        }


        acuantAndroidMobileSdkControllerInstance.setWebServiceListener(this);
        //acuantAndroidMobileSdkControllerInstance.setWatermarkText("Powered By Acuant", 0, 0, 0, 0);
        acuantAndroidMobileSdkControllerInstance.setWatermarkText(null, 0, 0, 30, 0);
        acuantAndroidMobileSdkControllerInstance.setFacialRecognitionTimeoutInSeconds(20);
        // DisplayMetrics metrics = this.getResources().getDisplayMetrics();

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int height = displaymetrics.heightPixels;
        int width = displaymetrics.widthPixels;
        // int minLength = (int) (Math.min(width, height) * 0.9);
        // int maxLength = (int) (minLength * 1.5);
        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Typeface currentTypeFace = textPaint.getTypeface();
        Typeface bold = Typeface.create(currentTypeFace, Typeface.BOLD);
        textPaint.setColor(Color.WHITE);
        if (!Util.isTablet(this)) {
            // Display display = getWindowManager().getDefaultDisplay();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            // if (display.getWidth() < 1000 || display.getHeight() < 1000) {
            if (displaymetrics.widthPixels < 1000 || displaymetrics.heightPixels < 1000) {
                textPaint.setTextSize(30);
            } else {
                textPaint.setTextSize(50);
            }

        } else {
            textPaint.setTextSize(30);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(bold);

        Paint subtextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        subtextPaint.setColor(Color.RED);
        if (!Util.isTablet(this)) {
            // Display display = getWindowManager().getDefaultDisplay();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            // if (display.getWidth() < 1000 || display.getHeight() < 1000) {
            if (displaymetrics.widthPixels < 1000 || displaymetrics.heightPixels < 1000) {
                textPaint.setTextSize(20);
            } else {
                subtextPaint.setTextSize(40);
            }
        } else {
            subtextPaint.setTextSize(25);
        }
        subtextPaint.setTextAlign(Paint.Align.LEFT);
        subtextPaint.setTypeface(Typeface.create(subtextPaint.getTypeface(), Typeface.BOLD));


        final String instrunctionStr = "Get closer until Red Rectangle appears and Blink";
        final String subInstString = "Analyzing...";
        Rect bounds = new Rect();
        textPaint.getTextBounds(instrunctionStr, 0, instrunctionStr.length(), bounds);
        int top = (int) (height * 0.05);
        if (Util.isTablet(this)) {
            top = top - 20;
        }
        int left = (width - bounds.width()) / 2;

        textPaint.getTextBounds(subInstString, 0, subInstString.length(), bounds);
        textPaint.setTextAlign(Paint.Align.LEFT);
        int subLeft = (width - bounds.width()) / 2;

        acuantAndroidMobileSdkControllerInstance.setInstructionText(instrunctionStr, left, top, textPaint);
        if (!Util.isTablet(this)) {
            // Display display = getWindowManager().getDefaultDisplay();
            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            // if (display.getWidth() < 1000 || display.getHeight() < 1000) {
            if (displaymetrics.widthPixels < 1000 || displaymetrics.heightPixels < 1000) {
                acuantAndroidMobileSdkControllerInstance.setSubInstructionText(subInstString, subLeft, top + 15, subtextPaint);
            } else {
                acuantAndroidMobileSdkControllerInstance.setSubInstructionText(subInstString, subLeft, top + 60, subtextPaint);
            }
        } else {
            acuantAndroidMobileSdkControllerInstance.setSubInstructionText(subInstString, subLeft, top + 30, subtextPaint);
        }


        //acuantAndroidMobileSdkControllerInstance.setShowActionBar(false);
        //acuantAndroidMobileSdkControllerInstance.setShowStatusBar(false);
        acuantAndroidMobileSdkControllerInstance.setFlashlight(false);
        //acuantAndroidMobileSdkControllerInstance.setFlashlight(0,0,50,0);
        //acuantAndroidMobileSdkControllerInstance.setFlashlightImageDrawable(getResources().getDrawable(R.drawable.lighton), getResources().getDrawable(R.drawable.lightoff));
        //acuantAndroidMobileSdkControllerInstance.setShowInitialMessage(true);
        acuantAndroidMobileSdkControllerInstance.setCropBarcode(false);
        acuantAndroidMobileSdkControllerInstance.setCaptureOriginalCapture(false);
        //acuantAndroidMobileSdkControllerInstance.setCropBarcodeOnCancel(true);
        //acuantAndroidMobileSdkControllerInstance.setPdf417BarcodeDialogWaitingBarcode("AcuantAndroidMobileSampleSDK","ALIGN AND TAP", 10, "Try Again", "Yes");
        //acuantAndroidMobileSdkControllerInstance.setCanShowBracketsOnTablet(true);
        // load several member variables

        acuantAndroidMobileSdkControllerInstance.setCardCroppingListener(this);
        acuantAndroidMobileSdkControllerInstance.setAcuantErrorListener(this);
    }

    /**
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //scanModel.clearImages();
        BitmapDrawable front_drawable = (BitmapDrawable) frontImageView.getDrawable();
        if (front_drawable != null) {
            Bitmap bitmap = front_drawable.getBitmap();
            if (bitmap != null) {
                bitmap.recycle();
            }
            frontImageView.setImageBitmap(null);
        }

        BitmapDrawable back_drawable = (BitmapDrawable) backImageView.getDrawable();
        if (back_drawable != null) {
            Bitmap bitmap = back_drawable.getBitmap();
            if (bitmap != null) {
                bitmap.recycle();
            }
            backImageView.setImageBitmap(null);
        }
        processedCardInformation = null;
        System.gc();
        System.runFinalization();
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onActivityResult(int requestCode, int resultCode, Intent data)");
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        updateUI();
    }

    @Override
    public void onCardImageCaptured() {
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.shutter);
        mp.start();
    }

    @Override
    public void onCardCroppingStart(Activity activity) {
        System.gc();
        System.runFinalization();
        if (Utils.LOG_ENABLED) {
            Utils.appendLog(TAG, "public void onCardCroppingStart(Activity activity)");
        }
        cardRegion = DataContext.getInstance().getCardRegion();
        if (progressDialog != null && progressDialog.isShowing()) {
            Util.dismissDialog(progressDialog);
        }
        Util.lockScreen(this);
        progressDialog = Util.showProgessDialog(activity, "Cropping image...");
        isCropping = true;
    }

    /**
     * Result from the CSSNMobileSDKController.showCameraInterface method when
     * popover == true
     */
    @Override
    public void onCardCroppingFinish(final Bitmap bitmap, int detectedCardType) {
        if (progressDialog != null && progressDialog.isShowing()) {
            Util.dismissDialog(progressDialog);
            progressDialog = null;
        }
        TempImageStore.setBitmapImage(bitmap);
        TempImageStore.setImageConfirmationListener(new ConfirmationListener() {
            @Override
            public void confimed() {
                if (Util.LOG_ENABLED) {
                    Utils.appendLog("appendLog", "public void onCardCroppedFinish(final Bitmap bitmap) - begin");
                }
                if (bitmap != null) {
                    updateModelAndUIFromCroppedCard(bitmap);
                } else {
                    // set an error to be shown in the onResume method.
                    scanModel.setErrorMessage("Unable to detect the card. Please try again.");
                    updateModelAndUIFromCroppedCard(originalImage);
                }
                Util.unLockScreen(ScanActivity.this);

                if (Util.LOG_ENABLED) {
                    Utils.appendLog("appendLog", "public void onCardCroppedFinish(final Bitmap bitmap) - end");
                }
                isCropping = false;
            }

            @Override
            public void retry() {
                showCameraInterface();
            }
        });

        Intent imageConfirmationIntent = new Intent(this, ImageConformationActivity.class);
        if (bitmap == null) {
            TempImageStore.setCroppingPassed(false);
        } else {
            TempImageStore.setCroppingPassed(true);
        }
        TempImageStore.setCardType(scanModel.getCurrentOptionType());
        startActivity(imageConfirmationIntent);

    }

    /**
     * Result from the CSSNMobileSDKController.showCameraInterface method when
     * popover == true
     */
    @Override
    public void onCardCroppingFinish(final Bitmap bitmap, final boolean scanBackSide, int detectedCardType) {
        //final Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.test_sa_dl);
        if (progressDialog != null && progressDialog.isShowing()) {
            Util.dismissDialog(progressDialog);
            progressDialog = null;
        }
        TempImageStore.setBitmapImage(bitmap);
        TempImageStore.setImageConfirmationListener(new ConfirmationListener() {
            @Override
            public void confimed() {
                presentCameraForBackSide(bitmap, scanBackSide);
            }

            @Override
            public void retry() {
                if ((cardRegion == Region.REGION_UNITED_STATES || cardRegion == Region.REGION_CANADA)
                        && scanModel.getCurrentOptionType() == CardType.DRIVERS_LICENSE
                        && isBackSide) {
                    acuantAndroidMobileSdkControllerInstance.setInitialMessageDescriptor(R.layout.tap_to_focus);
                    acuantAndroidMobileSdkControllerInstance.showCameraInterfacePDF417(scanActivity, CardType.DRIVERS_LICENSE, cardRegion);
                } else {
                    showCameraInterface();
                }
            }
        });
        Intent imageConfirmationIntent = new Intent(this, ImageConformationActivity.class);
        if (bitmap == null) {
            TempImageStore.setCroppingPassed(false);
        } else {
            TempImageStore.setCroppingPassed(true);
        }
        TempImageStore.setCardType(scanModel.getCurrentOptionType());
        startActivity(imageConfirmationIntent);
    }

    public void presentCameraForBackSide(final Bitmap bitmap, boolean scanBackSide) {
        if (Util.LOG_ENABLED) {
            Utils.appendLog("appendLog", "public void onCardCroppedFinish(final Bitmap bitmap) - begin");
        }
        cardRegion = DataContext.getInstance().getCardRegion();
        if (bitmap != null) {
            isBackSide = scanBackSide;
            if (isBackSide) {
                scanModel.setCardSideSelected(ScanModel.CardSide.FRONT);
            } else {
                scanModel.setCardSideSelected(ScanModel.CardSide.BACK);
            }

            if (scanModel.getCurrentOptionType() == CardType.DRIVERS_LICENSE && isBackSide) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showDuplexDialog();
                    }
                }, 100);
            }
            updateModelAndUIFromCroppedCard(bitmap);
        } else {
            // set an error to be shown in the onResume method.
            scanModel.setErrorMessage("Unable to detect the card. Please try again.");
            updateModelAndUIFromCroppedCard(originalImage);
        }

        Util.unLockScreen(this);

        if (Util.LOG_ENABLED) {
            Utils.appendLog("appendLog", "public void onCardCroppedFinish(final Bitmap bitmap) - end");
        }
        isCropping = false;
    }

    private void showDuplexDialog() {
        scanActivity = this;
        cardRegion = DataContext.getInstance().getCardRegion();
        Util.dismissDialog(showDuplexAlertDialog);
        Util.dismissDialog(alertDialog);
        showDuplexAlertDialog = new AlertDialog.Builder(this).create();
        showDuplexAlertDialog = Util.showDialog(this, getString(R.string.dl_duplex_dialog), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (cardRegion == Region.REGION_UNITED_STATES || cardRegion == Region.REGION_CANADA) {
                    acuantAndroidMobileSdkControllerInstance.setInitialMessageDescriptor(R.layout.tap_to_focus);
                    acuantAndroidMobileSdkControllerInstance.showCameraInterfacePDF417(scanActivity, CardType.DRIVERS_LICENSE, cardRegion);
                } else {
                    acuantAndroidMobileSdkControllerInstance.showManualCameraInterface(scanActivity, CardType.DRIVERS_LICENSE, cardRegion, isBackSide);
                }
                showDuplexAlertDialog.dismiss();
                isShowDuplexDialog = false;
            }
        });
        isShowDuplexDialog = true;
    }


    private void showFacialDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage(getString(R.string.facial_instruction_dialog))
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            acuantAndroidMobileSdkControllerInstance.setFacialListener(ScanActivity.this);
                            isProcessingFacial = acuantAndroidMobileSdkControllerInstance.showManualFacialCameraInterface(ScanActivity.this);
                            dialog.dismiss();
                        }
                    }).show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public void onPDF417Finish(String result) {
        sPdf417String = result;
    }

    @Override
    public void onOriginalCapture(Bitmap bitmap) {
        originalImage = bitmap;
    }

    @Override
    public void onCancelCapture(Bitmap croppedImage, Bitmap originalImage) {
        Utils.appendLog("Acuant", "onCancelCapture");
        if (croppedImage != null || originalImage != null) {
            if (croppedImage != null) {
                //scanModel.setBackSideCardImage(cImage);
                backImageView.setImageBitmap(croppedImage);
            } else if (originalImage != null) {
                //scanModel.setBackSideCardImage(oImage);
                backImageView.setImageBitmap(originalImage);
            }
        }
    }

    @Override
    public void onBarcodeTimeOut(Bitmap croppedImage, Bitmap originalImage) {
        final Bitmap cImage = croppedImage;
        final Bitmap oImage = originalImage;
        acuantAndroidMobileSdkControllerInstance.pauseScanningBarcodeCamera();
        AlertDialog.Builder builder = new AlertDialog.Builder(acuantAndroidMobileSdkControllerInstance.getBarcodeCameraContext());
        // barcode Dialog "ignore" option
        builder.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
                if (cImage != null) {
                    //scanModel.setBackSideCardImage(cImage);
                    backImageView.setImageBitmap(cImage);
                } else if (oImage != null) {
                    backImageView.setImageBitmap(oImage);
                    //scanModel.setBackSideCardImage(oImage);
                }
                acuantAndroidMobileSdkControllerInstance.finishScanningBarcodeCamera();
                dialog.dismiss();
            }
        });
        // barcode Dialog "retry" option
        builder.setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                acuantAndroidMobileSdkControllerInstance.resumeScanningBarcodeCamera();
                dialog.dismiss();
            }
        });
        //barcode Dialog title and main message
        builder.setMessage("Unable to scan the barcode?");
        builder.setTitle("AcuantMobileSDK");
        builder.create().show();

    }

    /**
     * Updates the model, and the ui. Called after acquiring a cropped card.
     */
    private void updateModelAndUIFromCroppedCard(final Bitmap bitmap) {
        switch (scanModel.getCardSideSelected()) {
            case FRONT:
                //scanModel.setFrontSideCardImage(bitmap);
                frontImageView.setImageBitmap(bitmap);
                break;

            case BACK:
                backImageView.setImageBitmap(bitmap);
                //scanModel.setBackSideCardImage(bitmap);
                break;

            default:
                throw new IllegalStateException("This method is bad implemented, there is not processing for the cardSide '"
                        + scanModel.getCardSideSelected() + "'");
        }
        updateUI();
    }

    /**
     * @param v - huh
     */
    public void frontSideCapturePressed(View v) {
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "public void frontSideCapturePressed(View v)");
        }
        isBackSide = false;

        //scanModel.clearImages();

        scanModel.setCardSideSelected(ScanModel.CardSide.FRONT);

        showCameraInterface();
    }

    /**
     * @param v - huh
     */
    public void backSideCapturePressed(View v) {
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "public void backSideCapturePressed(View v)");
        }
        isBackSide = true;

        //scanModel.clearBackImage();

        scanModel.setCardSideSelected(ScanModel.CardSide.BACK);
        showCameraInterface();
    }

    /**
     *
     */
    private void showCameraInterface() {
        final int currentOptionType = scanModel.getCurrentOptionType();
        cardRegion = DataContext.getInstance().getCardRegion();
        alertDialog = new AlertDialog.Builder(this).create();
        LicenseDetails license_details = DataContext.getInstance().getCssnLicenseDetails();
        if (currentOptionType == CardType.PASSPORT) {
            acuantAndroidMobileSdkControllerInstance.setWidth(AcuantUtil.DEFAULT_CROP_PASSPORT_WIDTH);
        } else if (currentOptionType == CardType.MEDICAL_INSURANCE) {
            acuantAndroidMobileSdkControllerInstance.setWidth(AcuantUtil.DEFAULT_CROP_MEDICAL_INSURANCE);
        } else {
            if (license_details != null && license_details.isAssureIDAllowed()) {
                acuantAndroidMobileSdkControllerInstance.setWidth(AcuantUtil.DEFAULT_CROP_DRIVERS_LICENSE_WIDTH_FOR_AUTHENTICATION);
            } else {
                acuantAndroidMobileSdkControllerInstance.setWidth(AcuantUtil.DEFAULT_CROP_DRIVERS_LICENSE_WIDTH);
            }
            //acuantAndroidMobileSdkControllerInstance.setWidth(2600);
        }
        acuantAndroidMobileSdkControllerInstance.setInitialMessageDescriptor(R.layout.align_and_tap);
        acuantAndroidMobileSdkControllerInstance.setFinalMessageDescriptor(R.layout.hold_steady);
        acuantAndroidMobileSdkControllerInstance.showManualCameraInterface(this, currentOptionType, cardRegion, isBackSide);
    }

    /**
     * Called after a tap in the driver's card button.
     *
     * @param v - huh
     */
    public void driverCardWithFacialButtonPressed(View v) {
        System.gc();
        System.runFinalization();
        // update the model
        processedCardInformation = null;
        processedFacialData = null;
        scanModel.setCurrentOptionType(CardType.DRIVERS_LICENSE);
        //scanModel.clearImages();
        isProcessing = false;
        isProcessingFacial = false;
        isFacialFlow = DataContext.getInstance().getCssnLicenseDetails() != null && DataContext.getInstance().getCssnLicenseDetails().isFacialAllowed();
        Intent regionList = new Intent(this, RegionList.class);
        this.startActivity(regionList);
        updateUI();
    }

    /**
     * Called after a tap in the passport card button.
     *
     * @param v - huh
     */

    public void passportCardWithFacialButtonPressed(View v) {
        System.gc();
        System.runFinalization();
        processedCardInformation = null;
        processedFacialData = null;
        scanModel.setCurrentOptionType(CardType.PASSPORT);
        isProcessing = false;
        isProcessingFacial = false;
        isFacialFlow = DataContext.getInstance().getCssnLicenseDetails() != null && DataContext.getInstance().getCssnLicenseDetails().isFacialAllowed();
        //scanModel.clearImages();
        updateUI();
    }

    /**
     * Called after a tap in the medical card button.
     *
     * @param v - huh
     */
    public void medicalCardButtonPressed(View v) {
        System.gc();
        System.runFinalization();
        processedCardInformation = null;
        processedFacialData = null;
        scanModel.setCurrentOptionType(CardType.MEDICAL_INSURANCE);
        //scanModel.clearImages();

        updateUI();
    }

    /**
     * calculate the width and height of the front side card image and resize them
     */
    private void resizeImageFrames(int cardType) {
        double aspectRatio = AcuantUtil.getAspectRatio(cardType);

        int height = (int) (layoutFrontImage.getLayoutParams().width * aspectRatio);
        int width = layoutFrontImage.getLayoutParams().width;

        layoutFrontImage.getLayoutParams().height = height;
        layoutFrontImage.getLayoutParams().width = width;

        layoutFrontImage.setLayoutParams(layoutFrontImage.getLayoutParams());

        if (cardType == CardType.MEDICAL_INSURANCE) {
            layoutBackImage.getLayoutParams().height = height;
            layoutBackImage.getLayoutParams().width = width;

            layoutBackImage.setLayoutParams(layoutBackImage.getLayoutParams());
        }
    }

    /**
     * Updates the card's frame layout, shows/hides the back side card frame,
     * highlights the selected option, and load the card images in the view.
     */
    public void updateUI() {
        if (Utils.LOG_ENABLED) {
            Utils.appendLog(TAG, "private void updateUI()");
        }

        if (scanModel.getErrorMessage() != null) {
            Util.dismissDialog(alertDialog);

            alertDialog = Util.showDialog(this, scanModel.getErrorMessage(), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    scanModel.setErrorMessage(null);
                    isShowErrorAlertDialog = false;
                }
            });
            isShowErrorAlertDialog = true;
        }

        // change orientation issues
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutCards.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            layoutCards.setOrientation(LinearLayout.VERTICAL);
        }

        if (scanModel.getCurrentOptionType() == -1) {
            // do not do any extra processing
            return;
        }

        // calculate the width and height of the front side card image and resize them
        resizeImageFrames(scanModel.getCurrentOptionType());

        // show/hide the front and back views in the layout
        switch (scanModel.getCurrentOptionType()) {
            case CardType.PASSPORT:
                txtTapToCaptureFront.setText(getResources().getString(R.string.tap_to_capture));
                showFrontSideCardImage();
                hideBackSideCardImage();
                break;

            case CardType.DRIVERS_LICENSE:
                showFrontSideCardImage();
                if (cardRegion == Region.REGION_UNITED_STATES || cardRegion == Region.REGION_CANADA) {
                    hideBackSideCardImage();
                } else {
                    showBackSideCardImage();
                }

                break;

            case CardType.MEDICAL_INSURANCE:
                txtTapToCaptureFront.setText(R.string.tap_to_capture_front_side);
                showFrontSideCardImage();
                showBackSideCardImage();
                break;
            case CardType.FACIAL_RECOGNITION:
                showFrontSideCardImage();
                showBackSideCardImage();
                break;

            default:
                throw new IllegalArgumentException(
                        "This method is wrong implemented, there is not processing for the card type '" + scanModel.getCurrentOptionType() + "'");

        }

        // update card in front image view
        //frontImageView.setImageBitmap( Util.getRoundedCornerBitmap(scanModel.getFrontSideCardImage(), this.getApplicationContext()));
        BitmapDrawable front_drawable = (BitmapDrawable) frontImageView.getDrawable();
        if (front_drawable != null) {
            Bitmap bitmap = front_drawable.getBitmap();
            if (bitmap != null) {
                hideFrontImageText();
            } else {
                showFrontImageText();
            }
        }

        // update card in back image view
        BitmapDrawable back_drawable = (BitmapDrawable) backImageView.getDrawable();
        if (back_drawable != null) {
            assert front_drawable != null;
            Bitmap bitmap = front_drawable.getBitmap();
            if (bitmap != null) {
                hideBackImageText();
            } else {
                showBackImageText();
            }
        }

        // update the process button
        BitmapDrawable drawable = (BitmapDrawable) frontImageView.getDrawable();
        if (drawable != null) {
            Bitmap bitmap = drawable.getBitmap();
            if (bitmap != null) {
                // processCardButton.setVisibility(View.VISIBLE);
                doneButton.setVisibility(View.VISIBLE);
            } else {
                // processCardButton.setVisibility(View.GONE);
                doneButton.setVisibility(View.GONE);
            }
        }


    }

    public void finishActivity(View v) {
        BitmapDrawable drawable = (BitmapDrawable) frontImageView.getDrawable();
        Bitmap front_bitmap = drawable.getBitmap();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        front_bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
        byte[] frontCard = stream.toByteArray();

        byte[] backCard = new byte[]{};
        BitmapDrawable back_drawable = (BitmapDrawable) backImageView.getDrawable();
        Bitmap back_bitmap = null;
        if (back_drawable != null) {
            back_bitmap = back_drawable.getBitmap();

            stream = new ByteArrayOutputStream();
            back_bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
            backCard = stream.toByteArray();
        } else {
            backCard = null;
        }

        Intent data = new Intent();
        data.putExtra("front_bitmap", frontCard);
        data.putExtra("back_bitmap", backCard);
        data.putExtra("region", DataContext.getInstance().getCardRegion());
        data.putExtra("type", scanModel.getCurrentOptionType());
        setResult(RESULT_OK, data);
        finish();
    }

    /**
     * Called by the process Button
     *
     * @param v - huh
     */
    public void processCard(View v) {
        if (isFacialFlow) {
            if (scanModel.getCurrentOptionType() == CardType.FACIAL_RECOGNITION || scanModel.getCurrentOptionType() == CardType.PASSPORT || scanModel.getCurrentOptionType() == CardType.DRIVERS_LICENSE) {
                isProcessingFacial = true;
                showFacialDialog();
            }
        }
        if (!isProcessingFacial) {
            if (progressDialog != null && progressDialog.isShowing()) {
                Util.dismissDialog(progressDialog);
            }
            progressDialog = Util.showProgessDialog(ScanActivity.this, "Capturing data ...");
            Util.lockScreen(this);
        }
        if ((!isProcessing && processedCardInformation == null) || scanModel.getCurrentOptionType() == CardType.MEDICAL_INSURANCE) {
            isProcessing = true;
            // check for the internet connection
            if (!Utils.isNetworkAvailable(this)) {
                String msg = getString(R.string.no_internet_message);
                Utils.appendLog(TAG, msg);
                Util.dismissDialog(alertDialog);
                alertDialog = Util.showDialog(this, msg, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        isShowErrorAlertDialog = false;
                    }
                });
                isShowErrorAlertDialog = true;
                return;
            }

            // process the card
            //progressDialog = Util.showProgessDialog(ScanActivity.this, "Capturing data ...");

            //Util.lockScreen(this);

            ProcessImageRequestOptions options = ProcessImageRequestOptions.getInstance();
            options.autoDetectState = true;
            options.stateID = -1;
            options.reformatImage = true;
            options.reformatImageColor = 0;
            options.DPI = 150;
            options.cropImage = false;
            options.faceDetec = true;
            options.signDetec = true;
            options.iRegion = DataContext.getInstance().getCardRegion();
            options.acuantCardType = scanModel.getCurrentOptionType();
            //options.logTransaction = false;
            BitmapDrawable drawable = (BitmapDrawable) frontImageView.getDrawable();
            Bitmap front_bitmap = drawable.getBitmap();

            BitmapDrawable back_drawable = (BitmapDrawable) backImageView.getDrawable();
            Bitmap back_bitmap = null;
            if (back_drawable != null) {
                back_bitmap = back_drawable.getBitmap();
            }
            acuantAndroidMobileSdkControllerInstance.callProcessImageServices(front_bitmap, back_bitmap, sPdf417String, this, options);
            resetPdf417String();
        }
    }

    public void processImageValidation(Bitmap faceImage, Bitmap idCropedFaceImage) {
        if (processedCardInformation != null) {
            isProcessingFacial = false;
        }
        scanModel.setCurrentOptionType(CardType.FACIAL_RECOGNITION);
        if (!Utils.isNetworkAvailable(this)) {
            String msg = getString(R.string.no_internet_message);
            Utils.appendLog(TAG, msg);
            Util.dismissDialog(alertDialog);
            alertDialog = Util.showDialog(this, msg, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    isShowErrorAlertDialog = false;
                }
            });
            isShowErrorAlertDialog = true;
            return;
        }

        //Util.lockScreen(this);

        ProcessImageRequestOptions options = ProcessImageRequestOptions.getInstance();
        options.acuantCardType = CardType.FACIAL_RECOGNITION;
        //options.logTransaction = true;
        acuantAndroidMobileSdkControllerInstance.callProcessImageServices(faceImage, idCropedFaceImage, null, this, options);
    }

    private void resetPdf417String() {
        sPdf417String = "";
    }


    /**
     *
     */
    @Override
    public void processImageServiceCompleted(Card card) {
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "public void processImageServiceCompleted(CSSNCard card, int status, String errorMessage)");
        }

        if (scanModel.getCurrentOptionType() != CardType.FACIAL_RECOGNITION) {
            isProcessing = false;
            processedCardInformation = card;
        } else {
            isProcessingFacial = false;
            processedFacialData = (FacialData) card;
        }
        System.gc();
        presentResults(processedCardInformation, processedFacialData);

    }


    public void presentResults(Card card, FacialData facialData) {
        if (!isProcessing && !isProcessingFacial) {
            Util.dismissDialog(progressDialog);
            showData(card, facialData);
        }

    }

    public void showData(Card card, FacialData facialData) {
        String dialogMessage = null;
        try {
            DataContext.getInstance().setCardType(scanModel.getCurrentOptionType());

            if (card == null || card.isEmpty()) {
                dialogMessage = "No data found for this license card!";
            } else {

                switch (scanModel.getCurrentOptionType()) {
                    case CardType.DRIVERS_LICENSE:
                        DataContext.getInstance().setProcessedLicenseCard((DriversLicenseCard) card);
                        break;

                    case CardType.MEDICAL_INSURANCE:
                        DataContext.getInstance().setProcessedMedicalCard((MedicalCard) card);
                        break;

                    case CardType.PASSPORT:
                        DataContext.getInstance().setProcessedPassportCard((PassportCard) card);
                        break;
                    case CardType.FACIAL_RECOGNITION:
                        if (processedCardInformation instanceof DriversLicenseCard) {
                            DriversLicenseCard dlCard = (DriversLicenseCard) processedCardInformation;
                            DataContext.getInstance().setProcessedLicenseCard(dlCard);
                            DataContext.getInstance().setCardType(CardType.DRIVERS_LICENSE);
                        } else if (processedCardInformation instanceof PassportCard) {
                            PassportCard passportCard = (PassportCard) processedCardInformation;
                            DataContext.getInstance().setProcessedPassportCard(passportCard);
                            DataContext.getInstance().setCardType(CardType.PASSPORT);
                        }
                        DataContext.getInstance().setProcessedFacialData(processedFacialData);
                        break;
                    default:
                        throw new IllegalStateException("There is not implementation for processing the card type '"
                                + scanModel.getCurrentOptionType() + "'");
                }

                Util.unLockScreen(ScanActivity.this);
                Intent showDataActivityIntent = new Intent(this, ShowDataActivity.class);
                showDataActivityIntent.putExtra("FACIAL", isFacialFlow);
                this.startActivityForResult(showDataActivityIntent, 100);
            }


        } catch (Exception e) {
            Utils.appendLog(TAG, e.getMessage());
            dialogMessage = "Sorry! Internal error has occurred, please contact us!";

        }

        if (dialogMessage != null) {
            Util.dismissDialog(alertDialog);
            alertDialog = Util.showDialog(this, dialogMessage, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    isShowErrorAlertDialog = false;
                }
            });
            isShowErrorAlertDialog = true;
        }
    }


    /**
     *
     */
    @Override
    public void validateLicenseKeyCompleted(LicenseDetails details) {
        isFirstLoad = false;
        Util.dismissDialog(progressDialog);
        Util.unLockScreen(ScanActivity.this);

        LicenseDetails cssnLicenseDetails = DataContext.getInstance().getCssnLicenseDetails();
        DataContext.getInstance().setCssnLicenseDetails(details);

        // update model
        scanModel.setState(State.VALIDATED);
        if (cssnLicenseDetails != null && cssnLicenseDetails.isLicenseKeyActivated()) {
            scanModel.setValidatedStateActivation(State.ValidatedStateActivation.ACTIVATED);
        } else {
            scanModel.setValidatedStateActivation(State.ValidatedStateActivation.NO_ACTIVATED);
        }
        // message dialogs
        acuantAndroidMobileSdkControllerInstance.enableLocationAuthentication(this);
        isValidating = false;

    }

    /**
     */
    private void showFrontSideCardImage() {
        layoutFrontImage.setClickable(true);
        layoutFrontImage.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    private void hideFrontSideCardImage() {
        layoutFrontImage.setClickable(false);
        layoutFrontImage.setVisibility(View.GONE);
    }

    /**
     *
     */
    private void showBackSideCardImage() {
        layoutBackImage.setClickable(true);
        layoutBackImage.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    private void hideBackSideCardImage() {
        layoutBackImage.setClickable(false);
        layoutBackImage.setVisibility(View.GONE);
    }

    /**
     *
     */
    private void showFrontImageText() {
        txtTapToCaptureFront.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    private void hideFrontImageText() {
        txtTapToCaptureFront.setVisibility(View.GONE);
    }

    /**
     *
     */
    private void showBackImageText() {
        txtTapToCaptureBack.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    private void hideBackImageText() {
        txtTapToCaptureBack.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean wschanged = sharedPref.getBoolean("WSCHANGED", false);
        if (wschanged && !isFirstLoad) {
            initializeSDK();
        }
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onResume()");
        }
        frontImageView.requestFocus();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //spdf417 = savedInstanceState.get(PDF417_STRING_KEY) != null ? (String) savedInstanceState.get(PDF417_STRING_KEY) : "";
        isShowErrorAlertDialog = savedInstanceState.getBoolean(IS_SHOWING_DIALOG_KEY, false);
        isProcessing = savedInstanceState.getBoolean(IS_PROCESSING_DIALOG_KEY, false);
        isCropping = savedInstanceState.getBoolean(IS_CROPPING_DIALOG_KEY, false);
        isValidating = savedInstanceState.getBoolean(IS_VALIDATING_DIALOG_KEY, false);
        isActivating = savedInstanceState.getBoolean(IS_ACTIVATING_DIALOG_KEY, false);
        isShowDuplexDialog = savedInstanceState.getBoolean(IS_SHOWDUPLEXDIALOG_DIALOG_KEY, false);
        if (progressDialog != null && progressDialog.isShowing()) {
            Util.dismissDialog(progressDialog);
        }
        if (isShowDuplexDialog) {
            showDuplexDialog();
        }
        if (isProcessing) {
            progressDialog = Util.showProgessDialog(ScanActivity.this, "Capturing data ...");
        }
        if (isCropping) {
            progressDialog = Util.showProgessDialog(ScanActivity.this, "Cropping image...");
        }
        if (isValidating) {
            progressDialog = Util.showProgessDialog(ScanActivity.this, "Validating License ..");
        }
        if (isActivating) {
            progressDialog = Util.showProgessDialog(ScanActivity.this, "Activating License ..");
        }
        if (isShowErrorAlertDialog) {
            alertDialog.show();
        }
        updateUI();
    }

    /**
     *
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onSaveInstanceState(Bundle outState)");
        }

        DataContext.getInstance().setScanModel(scanModel);
        //outState.putString(PDF417_STRING_KEY, this.pdf417);
        outState.putBoolean(IS_SHOWING_DIALOG_KEY, isShowErrorAlertDialog);
        outState.putBoolean(IS_PROCESSING_DIALOG_KEY, isProcessing);
        outState.putBoolean(IS_CROPPING_DIALOG_KEY, isCropping);
        outState.putBoolean(IS_ACTIVATING_DIALOG_KEY, isActivating);
        outState.putBoolean(IS_VALIDATING_DIALOG_KEY, isValidating);
        outState.putBoolean(IS_SHOWDUPLEXDIALOG_DIALOG_KEY, isShowDuplexDialog);
    }

    /**
     *
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (Utils.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onPause()");
        }
    }

    /**
     *
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        TempImageStore.cleanup();
        acuantAndroidMobileSdkControllerInstance.cleanup();
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "protected void onDestroy()");
        }
    }

    /**
     * @param bitmap
     * @return
     */
    private boolean saveBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("dd-mm-yyyy");
            String formattedDate = df.format(c.getTime());

            File file = new File("sdcard/CSSNCardCropped" + formattedDate + ".png");
            FileOutputStream fOutputStream = null;

            try {
                fOutputStream = new FileOutputStream(file);

                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOutputStream);

                fOutputStream.flush();
                fOutputStream.close();

                MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
            } catch (FileNotFoundException e) {
                if (Util.LOG_ENABLED) {
                    Utils.appendLog(TAG, e.getMessage());
                }
                Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show();
                return false;
            } catch (IOException e) {
                if (Util.LOG_ENABLED) {
                    Utils.appendLog(TAG, e.getMessage());
                }
                Toast.makeText(this, "Save Failed", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }

    @Override
    public void didFailWithError(int code, String message) {
        Utils.appendLog("didFailWithError", "didFailWithError:" + code + "message" + message);
        Util.dismissDialog(progressDialog);
        Util.unLockScreen(ScanActivity.this);
        String msg = message;
        if (code == ErrorType.AcuantErrorCouldNotReachServer) {
            msg = getString(R.string.no_internet_message);
        } else if (code == ErrorType.AcuantErrorUnableToCrop) {
            updateModelAndUIFromCroppedCard(originalImage);
        }
        if (alertDialog != null && !alertDialog.isShowing()) {
            alertDialog = Util.showDialog(this, msg, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    isShowErrorAlertDialog = false;
                }
            });
        }
        isShowErrorAlertDialog = true;
        if (Util.LOG_ENABLED) {
            Utils.appendLog(TAG, "didFailWithError:" + message);
        }
        // message dialogs
        isValidating = false;
        isProcessing = false;
        isActivating = false;
    }

    @Override
    public void onFacialRecognitionTimedOut(final Bitmap bitmap) {
        isProcessingFacial = false;
        onFacialRecognitionCompleted(bitmap);
    }

    @Override
    public void onFacialRecognitionCompleted(final Bitmap bitmap) {
        if (isShowErrorAlertDialog) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Util.lockScreen(ScanActivity.this);
                if (progressDialog != null && progressDialog.isShowing()) {
                    Util.dismissDialog(progressDialog);
                }
                progressDialog = Util.showProgessDialog(ScanActivity.this, "Capturing data ...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (isProcessing) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // process the card
                        if (processedCardInformation instanceof DriversLicenseCard) {
                            DriversLicenseCard dlCard = (DriversLicenseCard) processedCardInformation;
                            if (dlCard != null) {
                                processImageValidation(bitmap, dlCard.getFaceImage());
                            } else {
                                processImageValidation(bitmap, null);
                            }
                        } else if (processedCardInformation instanceof PassportCard) {
                            PassportCard passportCard = (PassportCard) processedCardInformation;
                            if (passportCard != null) {
                                processImageValidation(bitmap, passportCard.getFaceImage());
                            } else {
                                processImageValidation(bitmap, null);
                            }
                        }
                    }
                }).start();

            }
        });


    }

    @Override
    public void onFacialRecognitionCanceled() {
        isProcessingFacial = false;
    }


    //Override this only for API 23 and Above
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NotNull String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permission.PERMISSION_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showCameraInterface();

                } else {
                    // permission denied
                    Util.showDialog(this, "Denied permission.Please give camera permission to proceed.");
                }
                return;
            }

            case Permission.PERMISSION_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    acuantAndroidMobileSdkControllerInstance.enableLocationAuthentication(this);
                } else {
                    // permission denied
                    Util.showDialog(this, "Denied permission.Please give location permission to proceed.");
                }
            }
        }
    }
}
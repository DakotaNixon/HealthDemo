package macbeth.healthdemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private FitnessOptions fitnessOptions;
    private GoogleSignInClient googleClient;
    private GoogleSignInAccount account;
    private TextView tvName;
    private TextView tvSteps;
    private Button bRefresh;

    public final static int GOOGLE_RESPONSE_ID = 0;
    public final static int FITNESS_RESPONSE_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeGoogleClient();
        initializeFitnessOptions();

        tvName = findViewById(R.id.tv_name);
        tvName.setText("Waiting for Login...");
        tvSteps = findViewById(R.id.tv_steps);
        tvSteps.setText("Obtaining Data...");
        bRefresh = findViewById(R.id.b_refresh);
        bRefresh.setEnabled(false);

        bRefresh.setOnClickListener((view)-> {
            bRefresh.setEnabled(false);
            tvSteps.setText("Obtaining Data...");
            getWeeklySteps();
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        googleAutoLogin();
    }

    public void initializeGoogleClient() {
        GoogleSignInOptions googleOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        googleClient = GoogleSignIn.getClient(this, googleOptions);
    }

    public void initializeFitnessOptions() {
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .build();
    }

    private void googleAutoLogin() {
        account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            googleManualLogin();
        }
        else {
            Log.d("HealthDemo","Auto Logged in as "+account.getDisplayName());
            tvName.setText(account.getDisplayName());
            fitnessAutoConnect();
        }
    }

    private void googleManualLogin() {
        Intent signInIntent = googleClient.getSignInIntent();
        startActivityForResult(signInIntent, GOOGLE_RESPONSE_ID);
    }

    private void fitnessAutoConnect() {
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(this,FITNESS_RESPONSE_ID,account,fitnessOptions);
        } else {
            Log.d("HealthDemo","Auto Fitness Connected");
            getWeeklySteps();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_RESPONSE_ID) {
            try {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                account = task.getResult(ApiException.class);
                Log.d("HealthDemo","Logged in as "+account.getDisplayName());
                tvName.setText(account.getDisplayName());
                fitnessAutoConnect();
            } catch (ApiException e) {
                Log.d("HealthDemo","Failed to Login");
            }
        }
        else if (requestCode == FITNESS_RESPONSE_ID) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d("HealthDemo","Fitness Connected");
                getWeeklySteps();
            }
            else {
                Log.d("HealthDemo","Fitness Failed to Connect");
            }
        }
    }

    private void getWeeklySteps() {
        if (account == null) {
            Log.d("HealthDemo", "Not Logged In");
            return;
        }
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = DateFormat.getDateInstance();
        Log.d("HealthDemo", "Range Start: " + dateFormat.format(startTime));
        Log.d("HealthDemo", "Range End: " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();


        Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener((dataReadResponse) -> {
                    Log.d("HealthDemo", "onSuccess()");
                    int totalStepsForWeek = 0;
                    for (Bucket b : dataReadResponse.getBuckets()) {
                        Log.d("HealthDemo","Bucket:");
                        for (DataSet ds : b.getDataSets()) {
                            Log.d("HealthDemo","\tData Set");
                            for (DataPoint dp : ds.getDataPoints()) {
                                Log.d("HealthDemo", "\t\tData point:");
                                Log.d("HealthDemo", "\t\t\tType: " + dp.getDataType().getName());
                                Log.d("HealthDemo", "\t\t\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                                Log.d("HealthDemo", "\t\t\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                                for (Field field : dp.getDataType().getFields()) {
                                    Log.d("HealthDemo", "\t\t\t\tField: " + field.getName() + " Value: " + dp.getValue(field));
                                }
                                totalStepsForWeek += dp.getValue(Field.FIELD_STEPS).asInt();
                            }
                        }
                    }
                    tvSteps.setText(String.valueOf(totalStepsForWeek) + " Weekly Steps");
                    bRefresh.setEnabled(true);
                })
                .addOnFailureListener((error) -> {
                    Log.d("HealthDemo", "onFailure()");
                    tvSteps.setText("ERROR!");
                    bRefresh.setEnabled(true);
                });

    }

}


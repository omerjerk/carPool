package in.co.recex.carpool;

import java.io.IOException;
import java.sql.Timestamp; 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class MainActivity extends Activity {
	public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME =
            "onServerExpirationTimeMs";
    /**
     * Default lifespan (7 days) of a reservation until it is considered expired.
     */
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;

    /**
     * Substitute you own sender ID here.
     */
    String SENDER_ID = "323778949148";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "GCMDemo";

    TextView mDisplay;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    SharedPreferences prefs;
    Context context;

    String regid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        regid = getRegistrationId(context);

        if (regid.length() == 0) {
            registerBackground();
        } else {
        	System.out.println("Already Registered!");
        }
        gcm = GoogleCloudMessaging.getInstance(this);
    }
    
    /**
     * Gets the current registration id for application on GCM service.
     * <p>
     * If result is empty, the registration has failed.
     *
     * @return registration id, or empty string if the registration is not
     *         complete.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion || isRegistrationExpired()) {
            Log.v(TAG, "App version changed or registration expired.");
            return "";
        }
        return registrationId;
    }
    
    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        return getSharedPreferences(MainActivity.class.getSimpleName(), 
                Context.MODE_PRIVATE);
    }
    
    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Checks if the registration has expired.
     *
     * <p>To avoid the scenario where the device sends the registration to the
     * server but the server loses it, the app developer may choose to re-register
     * after REGISTRATION_EXPIRY_TIME_MS.
     *
     * @return true if the registration has expired.
     */
    private boolean isRegistrationExpired() {
        final SharedPreferences prefs = getGCMPreferences(context);
        // checks if the information is not stale
        long expirationTime =
                prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
        return System.currentTimeMillis() > expirationTime;
    }
    
    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration id, app versionCode, and expiration time in the 
     * application's shared preferences.
     */
    private void registerBackground() {
    	new RegisterAsyncTask().execute();
    }
    
    public class RegisterAsyncTask extends AsyncTask<Void, Void, String>{
				
		HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://www.recex.co.in/carPool/registerNewDevice.php");
	    
		protected String doInBackground (Void... params){
			String msg = "";
	        try {
	            if (gcm == null) {
	                gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
	            }
	            regid = gcm.register(SENDER_ID);
	            msg = "Device registered, registration id=" + regid;

	            // You should send the registration ID to your server over HTTP,
	            // so it can use GCM/HTTP or CCS to send messages to your app.

	            // For this demo: we don't need to send it because the device
	            // will send upstream messages to a server that echo back the message
	            // using the 'from' address in the message.

	            // Save the regid - no need to register again.
	            try {
	    	        // Add your data
	            	List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
	                nameValuePairs.add(new BasicNameValuePair("regID", regid));

	    	        // Execute HTTP Post Request
	    	        httpclient.execute(httppost);
	    	        System.out.println("Inside HTTP post");
	    	        
	    	    } catch (Exception e) {
	    	        // TODO Auto-generated catch block
	    	    	System.out.println("HTTP Post failed");
	    	    }
	            setRegistrationId(context, regid);
	        } catch (IOException ex) {
	            msg = "Error :" + ex.getMessage();
	        }
	        
	        return msg;
		}
		
		protected void onPostExecute (String result){
			System.out.println(result);
		}

    }
    
    /**
     * Stores the registration id, app versionCode, and expiration time in the
     * application's {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.v(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        long expirationTime = System.currentTimeMillis() + REGISTRATION_EXPIRY_TIME_MS;

        Log.v(TAG, "Setting registration expiry time to " +
                new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }
}

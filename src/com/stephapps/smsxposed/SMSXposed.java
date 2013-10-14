package com.stephapps.smsxposed;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.ByteArrayInputStream;

import com.stephapps.smsxposed.misc.Constants;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SMSXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources
{
	private EditText mEditText ;
	private String[] mSources , mDestinations, mDelayedSources, mDelayedDestinations;
	private boolean mTextWillBeChanged=false ;
	private String mAfterMsg;
	private Object mComposeMsgActivityObject;
	private TextWatcher mOriginalTextWatcher;
	private Context mContext;
	private Drawable mSMSSmallIcon;
	private static final String PACKAGE_NAME = SMSXposed.class.getPackage().getName();
	private WakeLock mSMSWakeLock;

	private static String MODULE_PATH = null;
	private int mSmsIconColor;
	
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
		

	}
	
	 @Override
	 public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!(resparam.packageName.equals("com.android.mms")))	return;

	  	XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);
	  	mSmsIconColor = prefs.getInt("sms_icon_color", Color.WHITE);
 		Resources tweakboxRes = XModuleResources.createInstance(MODULE_PATH, null);
		byte[] b = XposedHelpers.assetAsByteArray(tweakboxRes, "stat_notify_sms.png");
		ByteArrayInputStream is = new ByteArrayInputStream(b);
		mSMSSmallIcon = resizDrawable(Drawable.createFromStream(is, "stat_notify_sms.png"));
	
		//XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
		//resparam.res.setReplacement("com.android.mms", "drawable", "stat_notify_sms", modRes.fwd(R.drawable.stat_notify_sms));
		resparam.res.setReplacement("com.android.mms", "drawable", "stat_notify_sms", new XResources.DrawableLoader() {
			@Override
			public Drawable newDrawable(XResources res, int id) throws Throwable {
				Drawable d = mSMSSmallIcon.getConstantState().newDrawable();
				d.setColorFilter(mSmsIconColor,Mode.MULTIPLY );
				return d;
			}
		});
	}
	
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable 
    {  	
    	if (!(lpparam.packageName.equals("com.android.mms")))	return;

    	XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);
    	final boolean replaceSmileyWithEnterButton = prefs.getBoolean("replace_smiley_with_enter_button", false);
    	final boolean noFullScreenWithKeyboard = prefs.getBoolean("no_fullscreen_with_keyboard", false);
    	final boolean replacePuncutationInVoiceDictation = prefs.getBoolean("replace_punctuation_in_voice_dictation", false);
    	final boolean privacyMode = prefs.getBoolean("privacy_mode", false);
    	final boolean unlimitedTextbox = prefs.getBoolean("unlimited_textbox", false);
    	final boolean wakeOnNewSMS = prefs.getBoolean("wake_on_new_sms", false);
    	final boolean showSender = prefs.getBoolean("privacy_show_sender", false);
    	mSources 				= loadArray(Constants.SOURCES, prefs);
    	mDestinations 			= loadArray(Constants.DESTINATIONS, prefs);
    	mDelayedSources 		= loadArray(Constants.DELAYED_SOURCES, prefs);
    	mDelayedDestinations 	= loadArray(Constants.DELAYED_DESTINATIONS, prefs);
		
//    	findAndHookMethod("com.android.mms.ui.ComposeMessageActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
//    		@Override
//    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//    			Activity activity = (Activity)param.thisObject  ;  
//    			activity.setTheme(android.R.style.Theme_Holo);
//
//     		}
//    		
//    		@Override
//    		protected void afterHookedMethod(MethodHookParam param) throws Throwable 
//    		{
//    			    		}
//    	});

    	findAndHookMethod("com.android.mms.ui.ComposeMessageActivity", lpparam.classLoader, "initResourceRefs", new XC_MethodHook() {
    		@Override
    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    			
     		}
    		@Override
    		protected void afterHookedMethod(MethodHookParam param) throws Throwable 
    		{
    			mContext = ((Activity)param.thisObject).getApplicationContext();

    			mEditText = (EditText) XposedHelpers.getObjectField(param.thisObject, "mTextEditor");
    			
    			if (replaceSmileyWithEnterButton) replaceSmileyKeyWithEnterKey();
    			
    			if (noFullScreenWithKeyboard) removeFullScreenInLandScapeMode();
    			
    			if (unlimitedTextbox) mEditText.setMaxLines(Integer.MAX_VALUE);
    			
    			if (replacePuncutationInVoiceDictation)
    			{
	    			mOriginalTextWatcher = (TextWatcher) XposedHelpers.getObjectField(param.thisObject, "mTextEditorWatcher");
	    			switchTextChangedListener(mOriginalTextWatcher, mNewTextEditorWatcher);
	    			
	    			mComposeMsgActivityObject = param.thisObject;
    			}
    			
//    			IntentFilter filter = new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED);
//    	    	BroadcastReceiver mReceiver = new BroadcastReceiver() {
//    				
//    				@Override
//    				public void onReceive(Context context, Intent intent) {
//    					String action = intent.getAction();
//    		            if (action.equals(Intent.ACTION_INPUT_METHOD_CHANGED)) {
//    		                Log.i("SMSXposed","input method changed");
//    		                getInputInfos();
//    		            }
//    				}
//    			};
//    	    	mContext.registerReceiver(mReceiver, filter);
    		}
    	});
    	
    	if (privacyMode||wakeOnNewSMS)
    	{
	    	Class<?> contactClass = XposedHelpers.findClass("com.android.mms.data.Contact", lpparam.classLoader);
	    	findAndHookMethod("com.android.mms.transaction.MessagingNotification", lpparam.classLoader, "getNewMessageNotificationInfo", Context.class, boolean.class, String.class, String.class, String.class, long.class, long.class, Bitmap.class, contactClass, int.class, new XC_MethodHook() {
	    		@Override
	    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	    			if (wakeOnNewSMS)
	    			{
		    			PowerManager pm = (PowerManager) ((Context)param.args[0]).getSystemService(Context.POWER_SERVICE);
		    			if (pm.isScreenOn())
		    			{
		    				mSMSWakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE), "TAG");
		    				mSMSWakeLock.acquire();
		    				mSMSWakeLock.release();
		    			}
	    			}
	    	       
	    			if (privacyMode)
	    			{
		    			if (!showSender) 
		    				param.args[2] = "    ";
		    			param.args[3] = "    ";
	    			}
	    			return;
	     		}
	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable 
	    		{
	    			
	    		}
	    	});
    	}
    	
    	if (replacePuncutationInVoiceDictation)
    	{
	    	//meant to avoid errors , might be useless
	    	findAndHookMethod("com.android.mms.ui.ComposeMessageActivity", lpparam.classLoader, "resetMessage", new XC_MethodHook() {
	    		@Override
	    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	    			switchTextChangedListener(mNewTextEditorWatcher, mOriginalTextWatcher);
	     		}
	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable 
	    		{
	    			switchTextChangedListener(mOriginalTextWatcher, mNewTextEditorWatcher);    			
	    		}
	    	});
    	}
    }
    
    private final TextWatcher mNewTextEditorWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) 
        {
        	if (isVoiceInputMethodEnabled()==false) return;
        	
        	String beforeMsg = mEditText.getText().toString();
        	Log.i("SMSXposed","beforeTextChanged"+beforeMsg);
        	mAfterMsg = new String(beforeMsg); //TextUtils.replace(beforeMsg, mSources, mDestinations);
			
        	detectAndReplacePunctuation();
			
			Log.i("SMSXposed","afterMsg"+mAfterMsg);
			if ((mAfterMsg.trim().equals(beforeMsg.trim()))==false) 
			{
				Log.i("SMSXposed","not equals");
				mTextWillBeChanged=true;
			}
			else
			{
				mTextWillBeChanged=false;//strangely necessary or else will always be true even after 'afterTextChanged'
				delayedDetectAndReplacePunctuation();
			}
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) 
        {
            //if there's no change we call the original methods 
        	if (mTextWillBeChanged==false)
            {
	        	// This is a workaround for bug 1609057. Since onUserInteraction() is
	            // not called when the user touches the soft keyboard, we pretend it was
	            // called when textfields changes. This should be removed when the bug
	            // is fixed.
            	XposedHelpers.callMethod(mComposeMsgActivityObject, "onUserInteraction"); 

				Object workingMessage = XposedHelpers.getObjectField(mComposeMsgActivityObject, "mWorkingMessage");
				XposedHelpers.callMethod(workingMessage, "setText", s);  	
	           
	            XposedHelpers.callMethod(mComposeMsgActivityObject, "updateSendButtonState"); 

	            XposedHelpers.callMethod(mComposeMsgActivityObject, "updateCounter", s, start, before, count); 

	            XposedHelpers.callMethod(mComposeMsgActivityObject, "ensureCorrectButtonHeight"); 	           
            }
        }

        @Override
        public void afterTextChanged(Editable s) 
        {
        	if (mTextWillBeChanged)
        	{
        		Log.i("SMSXposed","afterTextChanged"+mAfterMsg);
            			
        		replaceInEditText();

				mTextWillBeChanged=false;
        	}
        }
    };
    
    private void detectAndReplacePunctuation()
    {
		int nbSources = mSources.length;
		for (int i=0;i<nbSources;i++)
		{
			mAfterMsg = mAfterMsg.replace(mSources[i], mDestinations[i]);
		}

    }
    
    //this method is used so some words are not replaced too fast, 
    //breaking other replacements (Ex : "point" "point d'interrogation" gives ". d'interrogation" or even "." the other word is not taken in account.
    private void delayedDetectAndReplacePunctuation()
    {
    	Handler handler= new Handler();
    	handler.postDelayed(new Runnable() {
			
			@Override
			public void run() 
			{
				int nbSources = mDelayedSources.length;
				if (nbSources>0)
				{
					String beforeMsg = mEditText.getText().toString();
		        	Log.i("SMSXposed","beforeTextChanged"+beforeMsg);
		        	mAfterMsg = new String(beforeMsg);
		        	
		        	for (int i=0;i<nbSources;i++)
		    		{
		        		mAfterMsg = mAfterMsg.replace(mDelayedSources[i], mDelayedDestinations[i]);
		    		}
					
					if ((mAfterMsg.trim().equals(beforeMsg.trim()))==false) 
					{
						replaceInEditText();
					}
				}
			}
		}, 1000);
    }
    
    private void replaceInEditText()
    {
    	mEditText.setSelection(0);//needed to avoid IndexOutOfBoundsException
		mEditText.setText(mAfterMsg);
		mEditText.setSelection(mEditText.getText().length());
    }
    
    private void switchTextChangedListener(TextWatcher oldTxtWatcher, TextWatcher newTxtWatcher)
    {
    	mEditText.removeTextChangedListener(oldTxtWatcher);
		mEditText.addTextChangedListener(newTxtWatcher);
    }
    
    private void replaceSmileyKeyWithEnterKey()
    {
    	mEditText.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE|InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    }
    
    private void removeFullScreenInLandScapeMode()
    {
    	mEditText.setImeOptions(mEditText.getImeOptions()|(EditorInfo.IME_FLAG_NO_FULLSCREEN));
    }
    
    public boolean isVoiceInputMethodEnabled() 
    {
        String id = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

        ComponentName defaultInputMethod = ComponentName.unflattenFromString(id);
//      ComponentName myInputMethod = new ComponentName(mContext, VoiceInputMethodService.class);

        return defaultInputMethod.getClassName().equals("com.google.android.voicesearch.ime.VoiceInputMethodService");
    }
    
//    private void getInputInfos()
//    {
//    	InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
//        List<InputMethodInfo> mInputMethodProperties = imm.getEnabledInputMethodList();
//
//        final int N = mInputMethodProperties.size();
//
//        for (int i = 0; i < N; i++) {
//
//            InputMethodInfo imi = mInputMethodProperties.get(i);
//
//            if (imi.getId().equals(Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD))) {
//            	if (imi.getComponent().toString().equals("ComponentInfo{com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService}"))
//            		mVoiceInputComponent = imi.getComponent();
//            	Log.i("SMSXposed",""+imi.getComponent().toString());
//                //imi contains the information about the keyboard you are using
//                break;
//            }
//        }
//    }
    
    private String[] loadArray(String arrayName, XSharedPreferences prefs)
    {
    	int size = prefs.getInt(arrayName + "_size", 0);  
        String array[] = new String[size];  
        for(int i=0;i<size;i++)  
            array[i] = prefs.getString(arrayName + "_" + i, null);  
        return array; 
    }
    
    private Drawable resizDrawable(Drawable image) {
        Bitmap b = ((BitmapDrawable)image).getBitmap();
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, 48*2, 48*2, false);
        return new BitmapDrawable(bitmapResized);
    }
    
}
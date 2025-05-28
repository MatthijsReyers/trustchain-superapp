package nl.tudelft.trustchain.p2playstore;

import static nl.tudelft.trustchain.p2playstore.utils.ExtensionUtils.DATA_DOT_EXTENSION;
import static nl.tudelft.trustchain.p2playstore.utils.ExtensionUtils.DEX_EXTENSION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import nl.tudelft.trustchain.p2playstore.databinding.ActivityExecutionBinding;


public class ExecutionActivity extends AppCompatActivity {
    private final static String FILE_NAME = "fileName";
    private ActivityExecutionBinding binding;
    private Fragment mainFragment;
    private FragmentManager manager;
    private String apkName;

    /**
     * Performs all required initials actions when loading the dynamic code:
     * - Retrieve filename of APK from MainActivityFOC
     * - Make sure the file is read-only to support Android 14+
     * - Dynamically load code of the APK using the DexClassLoader
     * - Restore the state of the dynamically loaded code, if any
     * - Load the dynamic code on a view on the users screen.
     *
     * @param savedInstanceState Default Android savedInstanceState
     */
    @SuppressLint({"ResourceType"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve filename of APK from MainActivityFOC.
        Bundle extras = this.getIntent().getExtras();
        assert extras != null;

        if (extras.containsKey(FILE_NAME)) {
            this.apkName = this.getIntent().getStringExtra(FILE_NAME);
            assert this.apkName != null;
        } else {
            this.printToast("No APK name supplied");
            return;
        }

        // If your app targets Android 14 (API level 34) or higher and uses Dynamic Code Loading
        // (DCL), all dynamically-loaded files must be marked as read-only. Otherwise, the system
        // throws an exception.
        //
        // https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading
        File apk = new File(this.apkName);
        // Set the file to read-only first to prevent race conditions
        if (!apk.canWrite() && apk.exists()) {
            Log.i("AppLoader", "APK is already read-only");
        } else if (!apk.setReadOnly()) {
            Log.w("AppLoader", String.format("APK %s could not be made read-only. Might fail on Android 14+.", this.apkName));
        }

        binding = ActivityExecutionBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        Context context = getApplicationContext();

        String activeApp = this.apkName.substring(this.apkName.lastIndexOf("/") + 1, this.apkName.lastIndexOf("."));

        final ClassLoader classLoader = new DexClassLoader(
                this.apkName,
                context.getCacheDir().getAbsolutePath(),
                null,
                this.getClass().getClassLoader()
        );

        try {
            String mainFragmentClass = getMainFragmentClass(this.apkName);
            if (mainFragmentClass == null) {
                printToast("MainFragment not found in APK.");
                return;
            }

            Class<?> fragmentClass = classLoader.loadClass(mainFragmentClass);
            this.mainFragment = (Fragment) fragmentClass.newInstance();
            Fragment.SavedState state = this.getState();
            if (state != null) {
                this.mainFragment.setInitialSavedState(state);
            }

            LinearLayout tmpLayout = new LinearLayout(context);
            tmpLayout.setId(View.generateViewId());

            this.manager = getSupportFragmentManager();
            FragmentTransaction transaction = this.manager.beginTransaction();
            transaction.add(tmpLayout.getId(), this.mainFragment, "mainFragment");
            transaction.commit();

            binding.llcontainer.addView(tmpLayout);
        } catch (Exception e) {
            this.printToast(e.toString());
            Log.i("personal", "Something went wrong");
        }
    }

    /**
     * This method is called by Android indicating that the user no longer interacts with the app and
     * that the state should be saved.
     */
    @Override
    public void onPause() {
        super.onPause();
        this.storeState();
    }

    /**
     * Stores the current state of the dynamically loaded code.
     */
    private void storeState() {
        // Store state next to apk
        String fileName = this.apkName + DATA_DOT_EXTENSION;
        try {
            FileOutputStream stream = new FileOutputStream(fileName);
            Parcel p = Parcel.obtain();
            Objects.requireNonNull(manager.saveFragmentInstanceState(mainFragment)).writeToParcel(p, 0);
            byte[] bytes = p.marshall();
            stream.write(bytes);
            stream.close();
            p.recycle();
        } catch (IOException e) {
            this.printToast(e.toString());
        }
    }

    /**
     * Retrieves the state of the dynamically loaded code (including any performed UI actions)
     *
     * @return the latest known state of the dynamically loaded code or null if it does not exist
     */
    private Fragment.SavedState getState() {
        // states are stored in the same directories as apks themselves (in the app specific files)
        String fileName = this.apkName + DATA_DOT_EXTENSION;
        try {
            Path path = Paths.get(fileName);
            byte[] data = Files.readAllBytes(path);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            Parcelable.Creator<Fragment.SavedState> classLoader = Fragment.SavedState.CREATOR;
            return classLoader.createFromParcel(parcel);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Retrieves the main fragment class from the specified APK.
     * This class can be in any package. The only requirement is the main fragment should be called exactly 'MainFragment'
     * <p>
     * Deprecation suppression required to use DexFile, which we use to loop through all classes.
     *
     * @param path to the APK.
     * @return the exact location of the main fragment class
     */
    @SuppressWarnings("deprecation")
    private String getMainFragmentClass(String path) {
        try {
            DexFile dx = DexFile.loadDex(
                    path,
                    File.createTempFile("opt", DEX_EXTENSION, getCacheDir()).getPath(),
                    0
            );
            for (Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements(); ) {
                String className = classNames.nextElement();
                if (className.contains("MainFragment") && !className.contains("$"))
                    return className;
            }
        } catch (IOException e) {
            Log.w("personal", "Error opening " + path, e);
        }
        return null;
    }

    /**
     * Display a short message on the screen (mainly for debugging purposes).
     */
    private void printToast(String s) {
        Toast.makeText(this.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }
}

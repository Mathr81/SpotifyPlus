package com.lenerd46.spotifyplus.scripting;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.lenerd46.spotifyplus.References;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;

public class UIManager implements SpotifyPlusApi {
    private android.content.Context context;

    @Override
    public void register(Scriptable scope, Context ctx) {
        ScriptableObject.putProperty(scope, "ui", this);
        context = (android.content.Context) ctx.getThreadLocal("context");
    }

    @JSFunction
    public int parseColor(String color) {
        return Color.parseColor(color);
    }

    @JSFunction
    public void attachToRoot(View view) {
        Activity activity = References.currentActivity;
        ViewGroup window = (ViewGroup) activity.getWindow().getDecorView();
        window.addView(view);
    }

    @JSFunction
    public TextView createTextView() {
        return new TextView(context);
    }

    @JSFunction
    public Button createButton() {
        return new Button(context);
    }

    @JSFunction
    public ImageView createImageView() {
        return new ImageView(context);
    }

    @JSFunction
    public EditText createEditText() {
        return new EditText(context);
    }

    @JSFunction
    public Switch createSwitch() {
        return new Switch(context);
    }

    @JSFunction
    public CheckBox createCheckBox() {
        return new CheckBox(context);
    }

    @JSFunction
    public RadioButton createRadioButton() {
        return new RadioButton(context);
    }

    @JSFunction
    public ProgressBar createProgressBar() {
        return new ProgressBar(context);
    }

    @JSFunction
    public SeekBar createSeekBar() {
        return new SeekBar(context);
    }

    @JSFunction
    public RatingBar createRatingBar() {
        return new RatingBar(context);
    }

    // Containers and Layouts

    @JSFunction
    public LinearLayout createLinearLayout() {
        return new LinearLayout(context);
    }

    @JSFunction
    public FrameLayout createFrameLayout() {
        return new FrameLayout(context);
    }

    @JSFunction
    public RelativeLayout createRelativeLayout() {
        return new RelativeLayout(context);
    }

    @JSFunction
    public ScrollView createScrollView() {
        return new ScrollView(context);
    }

    @JSFunction
    public RecyclerView createRecyclerView() {
        return new RecyclerView(context);
    }

    @JSFunction
    public CardView createCardView() {
        return new CardView(context);
    }

    @JSFunction
    public ViewPager2 createViewPager2() {
        return new ViewPager2(context);
    }

    @JSFunction
    public AlertDialog.Builder createAlertDialog() {
        return new AlertDialog.Builder(context);
    }

    // Other Stuff

    @JSFunction
    public Spinner createSpinner() {
        return new Spinner(context);
    }

    @JSFunction
    public AutoCompleteTextView createAutoCompleteTextView() {
        return new AutoCompleteTextView(context);
    }

    @JSFunction
    public DatePicker createDatePicker() {
        return new DatePicker(context);
    }

    @JSFunction
    public TimePicker createTimePicker() {
        return new TimePicker(context);
    }

    @JSFunction
    public Space createSpace() {
        return new Space(context);
    }

    @JSFunction
    public View createView() {
        return new View(context);
    }
}
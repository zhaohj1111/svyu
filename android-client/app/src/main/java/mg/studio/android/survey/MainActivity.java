package mg.studio.android.survey;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String json = "{\n" +
                "    \"survey\": {\n" +
                "        \"id\": \"12344134\",\n" +
                "        \"len\": \"3\",\n" +
                "        \"questions\": [\n" +
                "            {\n" +
                "                \"type\": \"single\",\n" +
                "                \"question\": \"How well do the professors teach at this university?\",\n" +
                "                \"options\": [\n" +
                "                    {\n" +
                "                        \"1\": \"Extremely well\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"2\": \"Very well\"\n" +
                "                    }\n" +
                "                ]\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"multiple\",\n" +
                "                \"question\": \"How effective is the teaching outside yur major at the univesrity?\",\n" +
                "                \"options\": [\n" +
                "                    {\n" +
                "                        \"1\": \"Extremetly effective\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"2\": \"Very effective\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"3\": \"Somewhat effective\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"4\": \"Not so effective\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"5\": \"Not at all effective\"\n" +
                "                    }\n" +
                "                ]\n" +
                "            },\n" +
                "            {\n" +
                "                \"type\": \"text\",\n" +
                "                \"question\": \"Test\"\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
        try {
            survey = Survey.parse(json);
        } catch (QuestionTypeNotSupportedException ex) {
            Log.wtf("Initialize", "Unexpected question type: " + ex.getType());
        } catch (JSONException ex) {
            Log.wtf("Initialize", "JSON format exception - General.");
        } catch (NumberFormatException ex) {
            Log.wtf("Initialize", "JSON format exception - Invalid number format.");
        }

        setContentView(R.layout.welcome);
        current = -1;
    }

    /**
     * Event handler for the primary button of each page.
     * @param sender The view that triggered the handler.
     */
    public void next(View sender) {
        if (finalized) {
            return;
        }

        if (current == -1) {
            CheckBox prerequisiteCheck = findViewById(R.id.welcome_check);
            if (!prerequisiteCheck.isChecked()) {
                return;
            }
        } else if (current >= 0 && current < survey.getLength()) {
            ISurveyResponse response = getResponse(survey.getQuestions()[current].getType());
            if (response.hasResponse()) {
                responses.add(response);
            } else {
                return;
            }
        } else {
            finalized = true;
            writeResultCache();
            navigateToSummary();
        }

        current++;
        if (current < survey.getLength()) {
            switch (survey.getQuestions()[current].getType()) {
                case Single:
                    bindLayout((SingleResponseQuestion) survey.getQuestions()[current]);
                    break;
                case Multiple:
                    bindLayout((MultipleResponseQuestion) survey.getQuestions()[current]);
                    break;
                case Text:
                    bindLayout((TextQuestion) survey.getQuestions()[current]);
                    break;
            }
        } else {
            setContentView(R.layout.finish_survey);
        }
    }

    /**
     * Reads previously stored response data.
     * @return A JSONArray object containing all the previously stored response data.
     */
    private JSONArray readResultCache() {
        File file = new File(this.getExternalFilesDir(null), "results.json");
        try {
            FileInputStream input = new FileInputStream(file);
            byte[] encoded = new byte[(int)file.length()];
            input.read(encoded);
            input.close();
            String json = new String(encoded);
            JSONArray cache = new JSONArray(json);
            return cache;
        } catch (FileNotFoundException ex) {
            Log.i("Result Load", "Result load failed because the file was not found.", ex);
            return null;
        } catch (IOException ex) {
            Log.e("Result Load", "Result load failed because IO operation failed.", ex);
            return null;
        } catch (JSONException ex) {
            Log.e("Result Load", "Result load failed because deserialization failed.", ex);
            return null;
        }
    }

    /**
     * Appends the responses of current session to the response store.
     * @return A boolean value signaling if the operation was successful.
     */
    private boolean writeResultCache() {
        String json = jsonSerializeResults(readResultCache());
        byte[] content = json.getBytes(Charset.forName("utf-8"));
        File file = new File(this.getExternalFilesDir(null), "results.json");
        return writeFile(file, content);
    }

    /**
     * Helper method for writing encoded file content.
     * @param file A File object describing the file to write to.
     * @param content A byte array containing encoded data to write.
     * @return A boolean value signaling if the operation was successful.
     */
    private boolean writeFile(File file, byte[] content) {
        try {
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream output = new FileOutputStream(file);
            output.write(content);
            output.flush();
            output.close();
            return true;
        } catch (IOException ex) {
            Log.e("Internal Save", "Result save failed because IO operation failed.", ex);
            return false;
        }
    }

    /**
     * Serialize he responses of current session and append the result to previously stored data.
     * @param existingRoot The root JSONArray for existing data.
     * @return Serialized JSON string.
     */
    private String jsonSerializeResults(JSONArray existingRoot) {
        try {
            if (existingRoot == null) {
                existingRoot = new JSONArray();
            }
            JSONObject jObject = new JSONObject();
            jObject.put("id", survey.getId());
            JSONArray responses = new JSONArray();
            for (ISurveyResponse r : this.responses) {
                responses.put(r.getResponse());
            }
            jObject.put("responses", responses);
            existingRoot.put(jObject);
            return existingRoot.toString();
        } catch (JSONException ex) {
            return existingRoot.toString();
        }
    }

    /**
     * A helper method for navigate to summary activity.
     */
    private void navigateToSummary() {
        Intent navIntent = new Intent(this, SummaryActivity.class);
        navIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        navIntent.putExtra(getPackageName() + ".survey", survey);
        navIntent.putExtra(getPackageName() + ".responses", responses);
        startActivity(navIntent);
        this.finish();
    }

    /**
     * Binds the app layout to a survey question.
     * @param question The question for the layout to bind to.
     */
    private void bindLayout(SingleResponseQuestion question) {
        setContentView(R.layout.question_single);
        bindLayoutTitle(question);
        ViewGroup optGroup = findViewById(R.id.opts);
        String[] options = question.getOptions();
        for (String opt : options) {
            RadioButton optBtn = new RadioButton(this);
            optBtn.setText(opt);
            optGroup.addView(optBtn);
        }
    }

    /**
     * Binds the app layout to a survey question.
     * @param question The question for the layout to bind to.
     */
    private void bindLayout(MultipleResponseQuestion question) {
        setContentView(R.layout.question_multiple);
        bindLayoutTitle(question);
        ViewGroup optGroup = findViewById(R.id.opts);
        String[] options = question.getOptions();
        for (String opt : options) {
            CheckBox optBtn = new CheckBox(this);
            optBtn.setText(opt);
            optGroup.addView(optBtn);
        }
    }

    /**
     * Binds the app layout to a survey question.
     * @param question The question for the layout to bind to.
     */
    private void bindLayout(TextQuestion question) {
        setContentView(R.layout.question_text);
        bindLayoutTitle(question);
    }

    /**
     * A helper method for binding the title of the question.
     * @param question The question for the layout to bind to.
     */
    private void bindLayoutTitle(ISurveyQuestion question) {
        TextView textView = findViewById(R.id.header);
        textView.setText(getString(R.string.questionHeader, current + 1));
        textView = findViewById(R.id.title);
        textView.setText(question.getQuestion());
    }

    /**
     * Gets the user response of current page.
     * @param type The type of the current question.
     * @return An ISurveyResponse object representing the user response.
     */
    private ISurveyResponse getResponse(QuestionType type) {
        ISurveyResponse response;
        switch (type) {
            case Single:
                response = new SingleResponse();
                ViewGroup opts = findViewById(R.id.opts);
                for (int i = 0; i < opts.getChildCount(); i++) {
                    RadioButton optBtn = (RadioButton)opts.getChildAt(i);
                    if (optBtn.isChecked()) {
                        response.setResponse(optBtn.getText().toString());
                        break;
                    }
                }
                break;
            case Multiple:
                response = new MultipleResponse();
                ViewGroup checks = findViewById(R.id.opts);
                for (int i = 0; i < checks.getChildCount(); i++) {
                    CheckBox check = (CheckBox)checks.getChildAt(i);
                    if (check.isChecked()) {
                        response.setResponse(check.getText().toString());
                    }
                }
                break;
            case Text:
                response = new SingleResponse();
                EditText inputBox = findViewById(R.id.inputBox);
                response.setResponse(inputBox.getText().toString());
                break;
            default:
                return null;
        }
        return response;
    }

    private int current;
    private boolean finalized = false;

    private ArrayList<ISurveyResponse> responses = new ArrayList<>();
    private Survey survey;
}
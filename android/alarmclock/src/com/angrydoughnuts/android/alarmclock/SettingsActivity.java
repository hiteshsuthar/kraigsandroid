package com.angrydoughnuts.android.alarmclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class SettingsActivity extends Activity {
  static public final String EXTRAS_ALARM_ID = "alarm_id";

  private final int TONE_SETTING = 0;
  private final int SNOOZE_SETTING = 1;
  private final int TOTAL_SETTINGS = 2;

  private final int MISSING_EXTRAS = -69;
  private final int TONE_PICK_ID = 1;
  private final int SNOOZE_PICK_ID = 2;

  private long alarmId;
  private DbAccessor db;
  private AlarmSettings settings;
  SettingsAdapter adapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings);

    alarmId = getIntent().getExtras().getLong(EXTRAS_ALARM_ID, MISSING_EXTRAS);
    if (alarmId == MISSING_EXTRAS) {
      throw new IllegalStateException("EXTRAS_ALARM_ID not supplied in intent.");
    }

    db = new DbAccessor(getApplicationContext());

    settings = db.readAlarmSettings(alarmId);

    Button okButton = (Button) findViewById(R.id.settings_ok);
    okButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        db.writeAlarmSettings(alarmId, settings);
        finish();
      }
    });
    Button cancelButton = (Button) findViewById(R.id.settings_cancel);
    cancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
    Button deleteButton = (Button) findViewById(R.id.settings_delete);
    if (alarmId == AlarmSettings.DEFAULT_SETTINGS_ID) {
      deleteButton.setVisibility(View.GONE);
    }
    deleteButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // TODO(cgallek): Add confirmation dialog.
        db.deleteAlarm(alarmId);
        finish();
      }
    });

    Setting[] settingsObjects = new Setting[TOTAL_SETTINGS];
    settingsObjects[TONE_SETTING] = new Setting() {
      @Override
      public String name() { return "Tone"; }
      @Override
      public String value() { return "" + settings.getTone().toString(); }
      
    };
    settingsObjects[SNOOZE_SETTING] = new Setting() {
      @Override
      public String name() { return "Snooze (minutes)"; }
      @Override
      public String value() { return "" + settings.getSnoozeMinutes(); }      
    };
    
    ListView settingsList = (ListView) findViewById(R.id.settings_list);
    adapter = new SettingsAdapter(getApplicationContext(), R.layout.settings_item, settingsObjects);
    settingsList.setAdapter(adapter);
    settingsList.setOnItemClickListener(new SettingsListClickListener());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    db.closeConnections();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }

    switch (requestCode) {
      case TONE_PICK_ID:
        // This can be null if 'Silent' was selected, but it was disabled
        // above so that should never happen.
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) {
          // This should never happen, but fall back to the phone ringer just
          // in case.
          // TODO(cgallek): this url doesn't actually resolve in the media
          // player.  Change it.
          uri = Settings.System.DEFAULT_RINGTONE_URI;
        }
        adapter.notifyDataSetChanged();
        settings.setTone(uri);
      default:
        super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case SNOOZE_PICK_ID:
        // TODO(cgallek): this is silly...
        final CharSequence[] items = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31", "32", "33", "34", "35", "36", "37", "38", "39", "40",
            "41", "42", "43", "44", "45", "46", "47", "48", "49", "50",
            "51", "52", "53", "54", "55", "56", "57", "58", "59", "60" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // TODO(cgallek): move this to strings.xml
        builder.setTitle("Snooze (in minutes)");
        builder.setSingleChoiceItems(items, settings.getSnoozeMinutes() - 1,
            new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int item) {
            settings.setSnoozeMinutes(item + 1);
            adapter.notifyDataSetChanged();
            dismissDialog(SNOOZE_PICK_ID);
          }
        });
        return builder.create();
      default:
        return super.onCreateDialog(id);
    }
  }

  class SettingsListClickListener implements OnItemClickListener {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      switch (position) {
        case TONE_SETTING:
          // TODO(cgallek): this is the wrong ringtone.
          Uri current_tone = settings.getTone();
          Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, true);
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default Alarm Tone");
          i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current_tone);
          startActivityForResult(i, TONE_PICK_ID);
          break;
        case SNOOZE_SETTING:
          showDialog(SNOOZE_PICK_ID);
          break;
      }
    }
  }

  private abstract class Setting {
    public abstract String name();
    public abstract String value();
  }

  private class SettingsAdapter extends ArrayAdapter<Setting> {
    public SettingsAdapter(Context context, int textViewResourceId,
        Setting[] settingsObjects) {
      super(context, textViewResourceId, settingsObjects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = getLayoutInflater();
      View row = inflater.inflate(R.layout.settings_item, null); 
      TextView name = (TextView) row.findViewById(R.id.setting_name);
      TextView value = (TextView) row.findViewById(R.id.setting_value);
      Setting setting = getItem(position);
      name.setText(setting.name());
      value.setText(setting.value());
      return row;
    }
  }
}

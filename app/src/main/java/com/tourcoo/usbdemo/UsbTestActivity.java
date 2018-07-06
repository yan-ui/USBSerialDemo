package com.tourcoo.usbdemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsbTestActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";   //记录标识

    private UsbManager manager;   //USB管理器
    private UsbDevice mUsbSerialDevice;  //找到的USB转串口设备
    private UsbSerialPort mUsbSerialPort;
    private UsbDeviceConnection mDeviceConnection;
    private PendingIntent intent;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private TextView tv_data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_test);
        tv_data = findViewById(R.id.tvtitle);

        intent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        initUsbDevice();
        checkPermission();

    }


    private void initUsbDevice() {

        // 获取USB设备
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            return;
        } else {
            Log.i(TAG, "usb设备：" + String.valueOf(manager.toString()));
        }

        final List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            // 在这里添加处理设备的代码 此处的数据需要根据具体设备进行动态替换   xml文件夹下的device_filter文件也需要同步变化
            if (driver.getDevice().getVendorId() == 1027 && driver.getDevice().getProductId() == 24577 && driver.getDevice().getDeviceId() == 1008) {
                mUsbSerialDevice = driver.getDevice();
                mUsbSerialPort = driver.getPorts().get(0);
                Log.i(TAG, "找到设备");
            }

        }

    }

    // 检查权限
    private void checkPermission() {
        if (mUsbSerialDevice == null) {
            Log.i(TAG, "没有找到设备");
            return;
        }

        // 判断是否有权限
        if (manager.hasPermission(mUsbSerialDevice)) {
            initSerialParam();

        } else {
            Log.i(TAG, "没有权限,正在获取权限...");
            manager.requestPermission(mUsbSerialDevice, intent);
            if (manager.hasPermission(mUsbSerialDevice)) {
                Log.e(TAG, "获取权限成功");
                initSerialParam();
            } else {
                Log.e(TAG, "获取权限失败");
                Toast.makeText(this, "权限获取失败，请重启再试", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        onDeviceStateChange();
    }

    private void initSerialParam() {
        // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
        mDeviceConnection = manager.openDevice(mUsbSerialDevice);
        if (mDeviceConnection == null) {
            return;
        }

        try {
            mUsbSerialPort.open(mDeviceConnection);
            mUsbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            try {
                mUsbSerialPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            mUsbSerialPort = null;
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mUsbSerialPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mUsbSerialPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }


    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "run: " + new String(data));
                            //此处必须使用append  有时候获取数据时数据分成了两次返回，需要使用append进行数据的拼接，防止数据获取不完整
                            tv_data.append(new String(data));
                            tv_data.setMovementMethod(ScrollingMovementMethod.getInstance());
                            int offset = tv_data.getLineCount() * tv_data.getLineHeight();
                            if (offset > (tv_data.getHeight() - tv_data.getLineHeight())) {
                                tv_data.scrollTo(0, offset - tv_data.getHeight() + tv_data.getLineHeight());
                            }
                        }
                    });
                }
            };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }


    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, intent.getAction());
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                Log.e("granted", granted + "");
            }
        }
    };


}


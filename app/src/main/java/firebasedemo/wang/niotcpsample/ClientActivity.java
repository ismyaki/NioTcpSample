package firebasedemo.wang.niotcpsample;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

public class ClientActivity extends AppCompatActivity {
	private String tag = "ClientActivity";
	private Handler handler;
	private HandlerThread handlerThread = new HandlerThread(tag);
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_client);
		
		setTitle("CLIENT");
		
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper());
		
		initView();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		close(clientSocketChannel , clientSelector);
	}
	
	private TextView tv_ip;
	private EditText et_ip;
	private EditText et_port;
	private EditText et_txt;
	private Button btn_send;
	private Button btn_init_client;
	private ListView lv;
	private ArrayAdapter<String> arrayAdapter;
	private void initView(){
		tv_ip = findViewById(R.id.tv_ip);
		tv_ip.setText("MY IP ADDRESS : " + getIpAddressString());
		
//		tv_txt = findViewById(R.id.tv_txt);
		et_ip = findViewById(R.id.et_ip);
		et_port = findViewById(R.id.et_port);
		et_txt = findViewById(R.id.et_txt);
		
		btn_send = findViewById(R.id.btn_send);
		btn_init_client = findViewById(R.id.btn_init_client);
		//
		btn_init_client.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						String ip = et_ip.getText().toString().trim();
						int port = 1234;
						try{
							port = Integer.parseInt(et_port.getText().toString().trim());
						}catch (Exception e){
							e.printStackTrace();
						}
						initClient(ip , port);
					}
				});
			}
		});
		
		btn_send.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						String txt = et_txt.getText().toString().trim();
						send(clientSocketChannel , txt);
					}
				});
			}
		});
		
		//
		lv = findViewById(R.id.lv);
		arrayAdapter = new ArrayAdapter<String>(
				this,
				R.layout.list_item,
				new ArrayList<String>());
		lv.setAdapter(arrayAdapter);
	}
	
	private void setText(final String txt){
		Log.e(tag , "setText " + txt);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
				String time = format.format(new Date());
				
				String str =  time + " " + txt;
				
				arrayAdapter.add(str);
				lv.smoothScrollToPosition(arrayAdapter.getCount()-1);
			}
		});
	}
	
	/**取得 ip address*/
	public String getIpAddressString() {
		try {
			for (Enumeration<NetworkInterface> enNetI = NetworkInterface
					.getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
				NetworkInterface netI = enNetI.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = netI.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress();
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private SocketChannel clientSocketChannel;
	private Selector clientSelector;
	private long CLIENT_TIMEOUT = 1000 * 5;
	/**
	 * 初始化 Client
	 * */
	private void initClient(String ip , int port){
		Log.e(tag , "initClient " + ip + ":" + port);
		setText("連線中...");
		try {
			//初始化連線
			clientSocketChannel = SocketChannel.open();
			clientSocketChannel.configureBlocking(false);
			
			clientSelector = Selector.open();
			clientSocketChannel.register(clientSelector,  SelectionKey.OP_CONNECT);
			
			InetSocketAddress socketAddress = new InetSocketAddress(ip, port);
			clientSocketChannel.connect(socketAddress);
			
			int readyChannels = clientSelector.select(CLIENT_TIMEOUT);//這行會卡住等待連線
			if (readyChannels <= 0){
				setText("TIME OUT");
				close(clientSocketChannel , clientSelector);
				return;
			}
			clientSocketChannel.finishConnect();
			
			setText("initClient : " + ip  + ":" + port);
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					read(clientSocketChannel, clientSelector);
				}
			}).start();

//			new Thread(new Runnable() {
//				@Override
//				public void run() {
//					isConnect(clientSocketChannel, clientSelector);
//				}
//			}).start();
		} catch (IOException e) {
			e.printStackTrace();
			setText("initClient error " + e.getMessage());
		}
	}
	
	private long READ_TIMEOUT = 1000 * 5;
	/**讀取資料*/
	private void read(SocketChannel socketChannel , Selector selector){
		try {
			while (true){
				setText("等待訊息...");
				socketChannel.configureBlocking(false);
				socketChannel.register(selector, SelectionKey.OP_READ);
				int readyChannels = selector.select(READ_TIMEOUT);//這行會卡住等待連線
				
				ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
				int recviveLength = socketChannel.read(byteBuffer);
				Log.e(tag , "recviveLength " + recviveLength);
				if (recviveLength > 0){
					//收到訊息
					String str = new String(byteBuffer.array(), StandardCharsets.UTF_8);
					setText("收到訊息 " + socketChannel.socket().getInetAddress() + " " + str.trim());
				}else if (recviveLength == 0){
					//TIME OUT
//					setText("READ TIME OUT");
				}else if (recviveLength == -1){
					//斷線
					setText("斷線 " + socketChannel.socket().getLocalAddress());
					break;
				}
				if (recviveLength >= 0 && socketChannel != null && socketChannel.isConnected() == true){
				
				}else{
					break;
				}
			}
		} catch (ClosedChannelException e) {
			e.printStackTrace();
			setText("read error " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			setText("read error " + e.getMessage());
		} catch (Exception e){
			e.printStackTrace();
			setText("read error " + e.getMessage());
		}
	}
	
	/**發送訊息*/
	private void send(SocketChannel socketChannel , String str){
		try {
			byte[] data = str.getBytes("UTF-8");
			ByteBuffer byteBuffer = ByteBuffer.wrap(data);
			int sendLength = socketChannel.write(byteBuffer);
			Log.e(tag , "sendLength " + sendLength);
			setText("發送訊息 " + str);
		} catch (IOException e) {
			e.printStackTrace();
			setText("send error " + e.getMessage());
		}
	}
	
	/**連線中斷*/
	private void close(SocketChannel socketChannel , Selector selector){
		try {
			if(selector != null) {
				selector.close();
				selector = null;
			}
			
			if(socketChannel != null) {
				socketChannel.close();
				socketChannel = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

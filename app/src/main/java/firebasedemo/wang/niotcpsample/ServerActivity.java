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
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;

public class ServerActivity extends AppCompatActivity {
	private String tag = "ServerActivity";
	private Handler handler;
	private HandlerThread handlerThread = new HandlerThread(tag);
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server);
		
		setTitle("SERVICE");
		
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper());
		
		initView();
	}
	
	
	@Override
	protected void onPause() {
		super.onPause();
		close(serverSocketChannel , servSelector);
	}
	
	private TextView tv_ip;
	private EditText et_port;
	private EditText et_txt;
	private Button btn_send;
	private Button btn_init_service;
	private ListView lv;
	private ArrayAdapter<String> arrayAdapter;
	private void initView(){
		tv_ip = findViewById(R.id.tv_ip);
		tv_ip.setText("MY IP ADDRESS : " + getIpAddressString());
		
		et_port = findViewById(R.id.et_port);
		et_txt = findViewById(R.id.et_txt);
		
		btn_send = findViewById(R.id.btn_send);
		btn_init_service = findViewById(R.id.btn_init_service);
		//
		btn_init_service.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						int port = 1234;
						try{
							port = Integer.parseInt(et_port.getText().toString().trim());
						}catch (Exception e){
							e.printStackTrace();
						}
						initService(port);
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
						send(txt);
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
	
	/**發送訊息*/
	private void send(String str){
		for (String key : clientMap.keySet()) {
			SocketChannel socketChannel = clientMap.get(key);
			send(socketChannel , str);
		}
	}
	
	/**發送訊息*/
	private void send(SocketChannel socketChannel , String str){
		try {
			byte[] data = str.getBytes("UTF-8");
			ByteBuffer byteBuffer = ByteBuffer.wrap(data);
			int sendLength = socketChannel.write(byteBuffer);
			setText("發送訊息 " +  socketChannel.socket().getInetAddress().getHostAddress() + " " + str);
		} catch (IOException e) {
			e.printStackTrace();
			setText("send error " + e.getMessage());
		}
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
	
	private ServerSocketChannel serverSocketChannel;
//	private SocketChannel servSocketChannel;
	private Selector servSelector;
	private long SERVICE_TIMEOUT = 1000 * 5;
	/**
	 * 初始化 Service
	 * */
	private void initService(int port){
		Log.e(tag , "initService " + port);
		try {
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);

//			InetSocketAddress socketAddress = new InetSocketAddress(SERVICE_IP , SERVICE_PORT);
			InetSocketAddress socketAddress = new InetSocketAddress(port);
			serverSocketChannel.socket().bind(socketAddress);
			
			servSelector = Selector.open();
			serverSocketChannel.register(servSelector, SelectionKey.OP_ACCEPT);
			setText("initService PORT : " + port);
//			int readyChannels = servSelector.select(SERVICE_TIMEOUT);//這行會卡住等待連線
//			if (readyChannels <= 0){
//				setText("TIME OUT");
//				close(serverSocketChannel , servSelector);
//				return;
//			}
//			servSocketChannel = serverSocketChannel.accept();
//			setText("收到連線 " + servSocketChannel.socket().getInetAddress());

//			new Thread(new Runnable() {
//				@Override
//				public void run() {
//					read(servSocketChannel, servSelector);
//				}
//			}).start();
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					accept(serverSocketChannel , servSelector);
				}
			}).start();
		} catch (ClosedChannelException e) {
			Log.e(tag , "ClosedChannelException");
			setText("initService error " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(tag , "IOException");
			setText("initService error " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private HashMap<String , SocketChannel> clientMap = new HashMap<>();
	private void accept(ServerSocketChannel serverSocketChannel , Selector selector){
		try {
			while (true){
				if(selector.isOpen() == false){
					break;
				}
				setText("等待訊息...");
				serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
				int readyChannels = selector.select(SERVICE_TIMEOUT);//這行會卡住等待連線
				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
				while (iter.hasNext()) {
					SelectionKey key = iter.next();
					if (key.isAcceptable()) {
						SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
						if (client != null){
							client.configureBlocking(false);
							client.register(selector, SelectionKey.OP_READ);
							clientMap.put(client.socket().getInetAddress().getHostAddress() , client);
							setText("收到連線" + client.socket().getInetAddress().getHostAddress());
						}
					} else if (key.isConnectable()) {
						SocketChannel client = (SocketChannel) key.channel();
						if (client != null){
							setText("Connect" + client.socket().getInetAddress().getHostAddress());
						}
					} else if (key.isReadable()) {
						SocketChannel client = (SocketChannel) key.channel();
						if (client != null){
							ByteBuffer buffer = ByteBuffer.allocate(2048);
							int readLength = client.read(buffer);
							if (readLength > 0){
								//收到訊息
								String str = new String(buffer.array(), StandardCharsets.UTF_8);
								setText("收到訊息 " + client.socket().getInetAddress().getHostAddress() + " " + str.trim());
								send(client , "收到訊息回傳 success");
							}else if (readLength == 0){
								//TIME OUT
//								setText("READ TIME OUT");
							}else if (readLength == -1){
								//斷線
								setText("斷線 " + client.socket().getInetAddress());
								clientMap.remove(client.socket().getInetAddress().getHostAddress());
								iter.remove();
								client.close();
							}
						}
					}
				}
			}
		} catch (ClosedChannelException e) {
			e.printStackTrace();
			setText("accept error " + e.getMessage());
		} catch (ClosedSelectorException e) {
			e.printStackTrace();
			setText("accept error " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			setText("accept error " + e.getMessage());
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
					setText("READ TIME OUT");
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
	
	/**連線中斷*/
	private void close(ServerSocketChannel serverSocketChannel , Selector selector){
		try {
			for (String key : clientMap.keySet()) {
				//可中斷指定的連線 這邊為全部關閉
				SocketChannel socketChannel = clientMap.get(key);
				socketChannel.close();
			}
			
			if(selector != null) {
				selector.close();
				selector = null;
			}
			
			if(serverSocketChannel != null) {
				serverSocketChannel.close();
				serverSocketChannel = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

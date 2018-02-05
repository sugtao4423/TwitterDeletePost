package sugtao4423.deletepost;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;

import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.UserStreamAdapter;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class DeletePost {

	private static Connection conn;
	private static Statement stmt;
	private static String[] notSaveUser = Config.notSaveUser;
	private static String savePath = Config.savePath;

	public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException{
		prepareSQL();

		Configuration conf = new ConfigurationBuilder()
				.setOAuthConsumerKey(Config.consumerKey)
				.setOAuthConsumerSecret(Config.consumerSecret)
				.setTweetModeExtended(true).build();
		AccessToken token = new AccessToken(Config.accessToken, Config.accessTokenSecret);

		TwitterStream stream = new TwitterStreamFactory(conf).getInstance(token);
		UserStreamAdapter adapter = new UserStreamAdapter(){
			@Override
			public void onStatus(Status status){
				if(!status.isRetweet())
					tweet(status);
			}
			@Override
			public void onDeletionNotice(StatusDeletionNotice del){
				delete(del.getStatusId());
			}
		};
		stream.addListener(adapter);
		stream.user();

		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run(){
				stream.shutdown();
				try {
					stmt.close();
					conn.close();
				} catch (SQLException e) {}
			}
		});
	}

	public static void prepareSQL() throws ClassNotFoundException, IOException, SQLException {
		Class.forName("org.sqlite.JDBC");
		String location = savePath + "/Twitter.db";
		File DB = new File(location);
		if(!DB.exists())
			DB.createNewFile();
		Properties prop = new Properties();
		prop.put("foreign_keys", "on");
		conn = DriverManager.getConnection("jdbc:sqlite:" + location, prop);
		stmt = conn.createStatement();
		stmt.execute("CREATE TABLE IF NOT EXISTS namelist(id INTEGER PRIMARY KEY AUTOINCREMENT, screen_name TEXT, UNIQUE(screen_name))");
		stmt.execute("CREATE TABLE IF NOT EXISTS vialist(id INTEGER PRIMARY KEY AUTOINCREMENT, via TEXT, UNIQUE(via))");
		stmt.execute("CREATE TABLE IF NOT EXISTS normalPost(content TEXT, name_id INTEGER NOT NULL, date TEXT, via_id INTEGER NOT NULL, medias TEXT, tweetId INTEGER, FOREIGN KEY (name_id) REFERENCES namelist(id), FOREIGN KEY (via_id) REFERENCES vialist(id))");
		stmt.execute("CREATE TABLE IF NOT EXISTS deletePost(content TEXT, name_id INTEGER NOT NULL, date TEXT, via_id INTEGER NOT NULL, medias TEXT, tweetId INTEGER, FOREIGN KEY (name_id) REFERENCES namelist(id), FOREIGN KEY (via_id) REFERENCES vialist(id))");
	}

	public static void tweet(Status status){
		boolean notSave = false;
		for(String s : notSaveUser){
			if(s.equals(status.getUser().getScreenName()))
				notSave = true;
		}
		if(notSave)
			return;

		TwitterMediaUtil mediaUtil = new TwitterMediaUtil(status);
		String content = mediaUtil.getContent();
		ArrayList<String> mediaUrls = mediaUtil.getUrls();
		String medias = "";
		for(String url : mediaUrls)
			medias += url + ",";
		String screenName = status.getUser().getScreenName();
		String date = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(status.getCreatedAt());
		String via = status.getSource().replaceAll("<.+?>", "");
		long tweetId = status.getId();

		normalPostInsert(content, screenName, date, via, medias, tweetId);
	}

	public static void normalPostInsert(String content, String screenName, String date, String via, String medias, long tweetId){
		content = content.replace("'", "''");
		screenName = screenName.replace("'", "''");
		via = via.replace("'", "''");
		medias = medias.replace("'", "''");
		String sql = "INSERT INTO normalPost VALUES(" +
					"'" + content + "'," +
					"(SELECT id FROM namelist WHERE screen_name = '" + screenName + "')," +
					"'" + date + "'," +
					"(SELECT id FROM vialist WHERE via = '" + via + "')," +
					"'" + medias + "'," +
					String.valueOf(tweetId) +
					")";
		for(int i = 0; i < 5; i++){
			try{
				stmt.execute(sql);
				break;
			}catch(SQLException e){
				if(e.getErrorCode() == 19){
					if(e.getMessage().equals("[SQLITE_CONSTRAINT_NOTNULL]  A NOT NULL constraint failed (NOT NULL constraint failed: normalPost.name_id)")){
						String addScreenName = "INSERT INTO namelist(screen_name) VALUES('" + screenName + "')";
						try{
							stmt.execute(addScreenName);
						}catch(SQLException e1){
						}
					}else if(e.getMessage().equals("[SQLITE_CONSTRAINT_NOTNULL]  A NOT NULL constraint failed (NOT NULL constraint failed: normalPost.via_id)")){
						String addVia = "INSERT INTO vialist(via) VALUES('" + via + "')";
						try{
							stmt.execute(addVia);
						}catch(SQLException e1){
						}
					}
				}else{
					break;
				}
			}
		}
	}

	public static void delete(long deleteId){
		String sql = "INSERT INTO deletePost(content, name_id, date, via_id, medias, tweetId) " +
					"SELECT content, name_id, date, via_id, medias, tweetId FROM normalPost WHERE tweetId = " + String.valueOf(deleteId);
		boolean isSuccess = false;
		for(int i = 0; i < 3; i++){
			try{
				stmt.execute(sql);
				isSuccess = true;
				break;
			}catch(SQLException e){
			}
		}

		if(!isSuccess)
			return;

		try{
			ResultSet resultSet = stmt.executeQuery("SELECT medias FROM deletePost WHERE tweetId = " + String.valueOf(deleteId));
			String[] medias = resultSet.getString(1).split(",", 0);
			for(String s : medias){
				URL url = new URL(s);
				String fileName = Paths.get(url.getPath()).getFileName().toString();
				saveFile(url, fileName);
			}
		}catch(SQLException | MalformedURLException e){
		}
	}

	public static void saveFile(URL url, String name){
		try {
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.connect();
			InputStream is = conn.getInputStream();
			FileOutputStream fos = new FileOutputStream(savePath + "/image/" + name);
			byte[] buffer = new byte[1024];
			int len;
			while((len = is.read(buffer)) > 0)
				fos.write(buffer, 0, len);
			fos.close();
			is.close();
			conn.disconnect();
		} catch (IOException e) {
		}
	}
}
package sugtao4423.deletepost;

import java.util.ArrayList;
import java.util.Collections;

import twitter4j.MediaEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.MediaEntity.Variant;

public class TwitterMediaUtil{

	private ArrayList<String> urls;
	private String content;

	public TwitterMediaUtil(Status status){
		content = status.getText();
		urls = new ArrayList<String>();

		URLEntity[] uentitys = status.getURLEntities();
		if(uentitys != null && uentitys.length > 0){
			for(URLEntity u : uentitys)
				content = content.replace(u.getDisplayURL(), u.getExpandedURL());
		}

		MediaEntity[] mentitys = status.getMediaEntities();
		if(mentitys != null && mentitys.length > 0){
			for(MediaEntity media : mentitys){
				if(isVideoOrGif(media)){
					String videoUrl = getVideoURLsSortByBitrate(mentitys);
					if(videoUrl != null)
						urls.add(videoUrl);
				}else{
					urls.add(media.getMediaURL());
				}
			}
		}

	}

	public ArrayList<String> getUrls(){
		return urls;
	}

	public String getContent(){
		return content;
	}

	private String getVideoURLsSortByBitrate(MediaEntity[] mentitys){
		ArrayList<VideoURLs> videos = new ArrayList<VideoURLs>();
		if(mentitys != null && mentitys.length > 0){
			for(MediaEntity media : mentitys){
				if(isVideoOrGif(media)){
					for(Variant v : media.getVideoVariants()){
						if(v.getContentType().equals("video/mp4"))
							videos.add(new VideoURLs(v.getBitrate(), v.getUrl()));
					}
					if(videos.size() == 0){
						for(Variant v : media.getVideoVariants()){
							if(v.getContentType().equals("video/mp4") || v.getContentType().equals("video/webm"))
								videos.add(new VideoURLs(v.getBitrate(), v.getUrl()));
						}
					}
					Collections.sort(videos);
				}
			}
		}
		if(videos.size() == 0)
			return null;
		else
			return videos.get(videos.size() - 1).url;
	}

	private boolean isVideoOrGif(MediaEntity ex){
		return (ex.getType().equals("video") || ex.getType().equals("animated_gif"));
	}

	private class VideoURLs implements Comparable<VideoURLs>{

		int bitrate;
		String url;

		public VideoURLs(int bitrate, String url){
			this.bitrate = bitrate;
			this.url = url;
		}

		@Override
		public int compareTo(VideoURLs another){
			return this.bitrate - another.bitrate;
		}
	}

}

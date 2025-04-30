package org.example;


import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.requests.data.playlists.GetListOfUsersPlaylistsRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistRequest;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Main {
	public static void main(String[] args) {
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

			scheduler.scheduleAtFixedRate(new Notifier(), 0, 5, TimeUnit.MINUTES);
	}

}

class Notifier implements Runnable {
	public void run() {
		// Set the Spotify API credentials
		String clientId = "d32e2a568a1646f9a9eef6ec28eb7670";
		String clientSecret = "b0a94132188a4453b6dab447db6b66e6";

		String accessToken;
		try {
			accessToken = getAccessToken(clientId, clientSecret);
		} catch (URISyntaxException | IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		String userID = "31rv3k7dfwpeggfpjs7bdssggpmm";

		SpotifyApi spotifyApi = new SpotifyApi.Builder()
				.setAccessToken(accessToken)
				.build();

		GetListOfUsersPlaylistsRequest getListOfUsersPlaylistsRequest = spotifyApi
				.getListOfUsersPlaylists(userID)
				.build();

		AbstractMap.SimpleEntry<PlaylistTrack, Playlist> latestTrack = getLatestAddedTrack(getListOfUsersPlaylistsRequest, spotifyApi);
		try {
			assert latestTrack != null;
			if (checkIfUpdated(latestTrack)) {
				notify(latestTrack);
			} else {
                System.out.println("No playlists updated - " + new Date());
			}
		} catch (java.text.ParseException e) {
			throw new RuntimeException(e);
		}
	}


	static String getAccessToken(String clientId, String clientSecret) throws URISyntaxException, IOException, InterruptedException {
		String form = "grant_type=client_credentials" +
				"&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
				"&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI("https://accounts.spotify.com/api/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		String accessToken = response.body().substring(response.body().indexOf(":") + 2, response.body().indexOf(",") - 1);
		return accessToken;
	}


	private static void notify(AbstractMap.SimpleEntry<PlaylistTrack, Playlist> latestTrack) {
		// Create a new frame
		Frame frame = new Frame("New Track Added");
		frame.setSize(300, 200);
		frame.setLayout(new FlowLayout());

		// Set the icon for the frame
		Image icon = Toolkit.getDefaultToolkit().getImage("src/main/resources/spotify.png");
		frame.setIconImage(icon);


		// Create a new label
		Label label = new Label("New Track Added: " + latestTrack.getKey().getTrack().getName());
		label.setAlignment(Label.CENTER);
		label.setSize(300, 100);

		Label label2 = new Label("Playlist: " + latestTrack.getValue().getName() );
		label2.setAlignment(Label.CENTER);
		label2.setSize(300, 100);
		Label label3 = new Label("Added at: " + latestTrack.getKey().getAddedAt());
		label3.setAlignment(Label.CENTER);
		label3.setSize(300, 100);

		// Create a new button
		Button button = new Button("Open in Spotify");
		button.addActionListener(e -> {
			try {
				Desktop.getDesktop().browse(new URI(latestTrack.getValue().getUri()));
			} catch (IOException | URISyntaxException ex) {
				ex.printStackTrace();
			}
		});

		// Add the label and button to the frame
		frame.add(label);
		frame.add(label2);
		frame.add(label3);
		frame.add(button);

		// Set the frame to be visible
		frame.setVisible(true);
		// Close the frame when the user clicks the close button or if 5 seconds have passed
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				frame.dispose();
			}
		});
		new java.util.Timer().schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				try {
					Desktop.getDesktop().browse(new URI(latestTrack.getValue().getUri()));
				} catch (IOException | URISyntaxException ex) {
					ex.printStackTrace();
				}
				frame.dispose();
			}
		}, 10000);

		System.out.println("New Track Added: " + latestTrack.getKey().getTrack().getName() + " in " + latestTrack.getValue().getName() + " - " + latestTrack.getKey().getAddedAt());
	}

	private static Boolean checkIfUpdated(AbstractMap.SimpleEntry<PlaylistTrack, Playlist> latestTrack) throws java.text.ParseException {
		String added_at = String.valueOf(latestTrack.getKey().getAddedAt());

		Date now = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
		String formattedDate = formatter.format(now);
		Date now_formatted = formatter.parse(formattedDate);
		Date added_at_formatted = formatter.parse(added_at);
		Date difference = new Date(now_formatted.getTime() - added_at_formatted.getTime());
		float seconds = (float) difference.getTime() / 1000;
		float minutes = seconds / 60;

		return minutes < 5;
	}

	private static AbstractMap.SimpleEntry<PlaylistTrack, Playlist> getLatestAddedTrack(GetListOfUsersPlaylistsRequest getListOfUsersPlaylistsRequest, SpotifyApi spotifyApi) {
		try {
			final Paging<PlaylistSimplified> playlistSimplifiedPaging = getListOfUsersPlaylistsRequest.execute();

			PlaylistSimplified[] playlists = playlistSimplifiedPaging.getItems();
			ArrayList<AbstractMap.SimpleEntry<PlaylistTrack, Playlist>> latestTracksInEveryPlaylist = getLatestTracks(playlists, spotifyApi);

			// sort the ArrayList latestTracksInEveryPlaylist by the getAddedAt field, in the latest track first order
			latestTracksInEveryPlaylist.sort((o1, o2) -> {
				try {
					return o2.getKey().getAddedAt().compareTo(o1.getKey().getAddedAt());
				} catch (NullPointerException e) {
					return 0;
				}
			});
			// get the first track in the sorted ArrayList latestTracksInEveryPlaylist
			return latestTracksInEveryPlaylist.getFirst();

		} catch (IOException | SpotifyWebApiException | ParseException e) {
			System.out.println("Error: " + e.getMessage());
		}
		return null;
	}

	private static ArrayList<AbstractMap.SimpleEntry<PlaylistTrack, Playlist>> getLatestTracks(PlaylistSimplified[] playlists, SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {
		// Array of Latest Tracks
		ArrayList<AbstractMap.SimpleEntry<PlaylistTrack, Playlist>> latestTracks = new ArrayList<>();
		for (PlaylistSimplified p : playlists) {
			GetPlaylistRequest getPlaylistRequest = spotifyApi
					.getPlaylist(p.getId())
					.build();
			Playlist playlist = getPlaylistRequest.execute();
			// get the date added of the first track
			PlaylistTrack[] tracks = playlist.getTracks().getItems();

			PlaylistTrack lastTrack = tracks[tracks.length - 1];
			// add the last track to the latest tracks array
			latestTracks.add(new AbstractMap.SimpleEntry<>(lastTrack, playlist));
		}
		return latestTracks;
	}
}

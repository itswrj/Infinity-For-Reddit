package ml.docilealligator.infinityforreddit.Post;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

import ml.docilealligator.infinityforreddit.API.RedditAPI;
import ml.docilealligator.infinityforreddit.Utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchPost {
    public static void fetchPost(Retrofit retrofit, String id, String accessToken, Locale locale, FetchPostListener fetchPostListener) {
        Call<String> postCall;
        if (accessToken == null) {
            postCall = retrofit.create(RedditAPI.class).getPost(id);
        } else {
            postCall = retrofit.create(RedditAPI.class).getPostOauth(id, APIUtils.getOAuthHeader(accessToken));
        }
        postCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    ParsePost.parsePost(response.body(), new ParsePost.ParsePostListener() {
                        @Override
                        public void onParsePostSuccess(Post post) {
                            fetchPostListener.fetchPostSuccess(post);
                        }

                        @Override
                        public void onParsePostFail() {
                            fetchPostListener.fetchPostFailed();
                        }
                    });
                } else {
                    fetchPostListener.fetchPostFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchPostListener.fetchPostFailed();
            }
        });
    }

    public static void fetchRandomPost(Retrofit retrofit, boolean isNSFW, FetchRandomPostListener fetchRandomPostListener) {
        Call<String> call;
        if (isNSFW) {
            call = retrofit.create(RedditAPI.class).getRandomNSFWPost();
        } else {
            call = retrofit.create(RedditAPI.class).getRandomPost();
        }

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    Log.i("asdasdf", "s " + response.body());
                    new ParsePost.ParseRandomPostAsyncTask(response.body(), isNSFW, new ParsePost.ParseRandomPostListener() {

                        @Override
                        public void onParseRandomPostSuccess(String postId, String subredditName) {
                            fetchRandomPostListener.fetchRandomPostSuccess(postId, subredditName);
                        }

                        @Override
                        public void onParseRandomPostFailed() {
                            fetchRandomPostListener.fetchRandomPostFailed();
                        }
                    }).execute();
                } else {
                    fetchRandomPostListener.fetchRandomPostFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchRandomPostListener.fetchRandomPostFailed();
            }
        });
    }

    public interface FetchPostListener {
        void fetchPostSuccess(Post post);

        void fetchPostFailed();
    }

    public interface FetchRandomPostListener {
        void fetchRandomPostSuccess(String postId, String subredditName);
        void fetchRandomPostFailed();
    }
}

package ml.docilealligator.infinityforreddit.Comment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import ml.docilealligator.infinityforreddit.API.RedditAPI;
import ml.docilealligator.infinityforreddit.Utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchComment {
    public static void fetchComments(Retrofit retrofit, @Nullable String accessToken, String article,
                                     String commentId, String sortType, boolean expandChildren,
                                     Locale locale, FetchCommentListener fetchCommentListener) {
        RedditAPI api = retrofit.create(RedditAPI.class);
        Call<String> comments;
        if (accessToken == null) {
            if (commentId == null) {
                comments = api.getPostAndCommentsById(article, sortType);
            } else {
                comments = api.getPostAndCommentsSingleThreadById(article, commentId, sortType);
            }
        } else {
            if (commentId == null) {
                comments = api.getPostAndCommentsByIdOauth(article, sortType, APIUtils.getOAuthHeader(accessToken));
            } else {
                comments = api.getPostAndCommentsSingleThreadByIdOauth(article, commentId, sortType,
                        APIUtils.getOAuthHeader(accessToken));
            }
        }

        comments.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    ParseComment.parseComment(response.body(), new ArrayList<>(),
                            locale, expandChildren, new ParseComment.ParseCommentListener() {
                                @Override
                                public void onParseCommentSuccess(ArrayList<Comment> expandedComments,
                                                                  String parentId, ArrayList<String> moreChildrenFullnames) {
                                    fetchCommentListener.onFetchCommentSuccess(expandedComments, parentId,
                                            moreChildrenFullnames);
                                }

                                @Override
                                public void onParseCommentFailed() {
                                    fetchCommentListener.onFetchCommentFailed();
                                }
                            });
                } else {
                    fetchCommentListener.onFetchCommentFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchCommentListener.onFetchCommentFailed();
            }
        });
    }

    public static void fetchMoreComment(Retrofit retrofit, @Nullable String accessToken,
                                        ArrayList<String> allChildren, int startingIndex,
                                        int depth, boolean expandChildren, Locale locale,
                                        FetchMoreCommentListener fetchMoreCommentListener) {
        if (allChildren == null) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (allChildren.size() <= startingIndex + i) {
                break;
            }
            stringBuilder.append(allChildren.get(startingIndex + i)).append(",");
        }

        if (stringBuilder.length() == 0) {
            return;
        }

        stringBuilder.deleteCharAt(stringBuilder.length() - 1);

        RedditAPI api = retrofit.create(RedditAPI.class);
        Call<String> moreComments;
        if (accessToken == null) {
            moreComments = api.getInfo(stringBuilder.toString());
        } else {
            moreComments = api.getInfoOauth(stringBuilder.toString(), APIUtils.getOAuthHeader(accessToken));
        }

        moreComments.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    ParseComment.parseMoreComment(response.body(), new ArrayList<>(), locale,
                            depth, expandChildren, new ParseComment.ParseCommentListener() {
                                @Override
                                public void onParseCommentSuccess(ArrayList<Comment> expandedComments,
                                                                  String parentId, ArrayList<String> moreChildrenFullnames) {
                                    fetchMoreCommentListener.onFetchMoreCommentSuccess(expandedComments,
                                            startingIndex + 100);
                                }

                                @Override
                                public void onParseCommentFailed() {
                                    fetchMoreCommentListener.onFetchMoreCommentFailed();
                                }
                            });
                } else {
                    fetchMoreCommentListener.onFetchMoreCommentFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchMoreCommentListener.onFetchMoreCommentFailed();
            }
        });
    }

    public interface FetchCommentListener {
        void onFetchCommentSuccess(ArrayList<Comment> expandedComments, String parentId, ArrayList<String> children);

        void onFetchCommentFailed();
    }

    public interface FetchMoreCommentListener {
        void onFetchMoreCommentSuccess(ArrayList<Comment> expandedComments, int childrenStartingIndex);

        void onFetchMoreCommentFailed();
    }
}

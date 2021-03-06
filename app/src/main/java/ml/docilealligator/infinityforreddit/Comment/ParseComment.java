package ml.docilealligator.infinityforreddit.Comment;

import android.os.AsyncTask;
import android.text.Html;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import ml.docilealligator.infinityforreddit.Utils.JSONUtils;
import ml.docilealligator.infinityforreddit.Utils.Utils;

import static ml.docilealligator.infinityforreddit.Comment.Comment.VOTE_TYPE_DOWNVOTE;
import static ml.docilealligator.infinityforreddit.Comment.Comment.VOTE_TYPE_NO_VOTE;
import static ml.docilealligator.infinityforreddit.Comment.Comment.VOTE_TYPE_UPVOTE;

public class ParseComment {
    public static void parseComment(String response, ArrayList<Comment> commentData, Locale locale,
                                    boolean expandChildren, ParseCommentListener parseCommentListener) {
        try {
            JSONArray childrenArray = new JSONArray(response);
            String parentId = childrenArray.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
                    .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.NAME_KEY);
            childrenArray = childrenArray.getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);

            new ParseCommentAsyncTask(childrenArray, commentData, locale, parentId, 0, expandChildren, parseCommentListener).execute();
        } catch (JSONException e) {
            e.printStackTrace();
            parseCommentListener.onParseCommentFailed();
        }
    }

    static void parseMoreComment(String response, ArrayList<Comment> commentData, Locale locale,
                                 int depth, boolean expandChildren, ParseCommentListener parseCommentListener) {
        try {
            JSONArray childrenArray = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
            new ParseCommentAsyncTask(childrenArray, commentData, locale, null, depth, expandChildren, parseCommentListener).execute();
        } catch (JSONException e) {
            e.printStackTrace();
            parseCommentListener.onParseCommentFailed();
        }
    }

    static void parseSentComment(String response, int depth, Locale locale,
                                 ParseSentCommentListener parseSentCommentListener) {
        new ParseSentCommentAsyncTask(response, depth, locale, parseSentCommentListener).execute();
    }

    private static void parseCommentRecursion(JSONArray comments, ArrayList<Comment> newCommentData,
                                              ArrayList<String> moreChildrenFullnames, int depth, Locale locale) throws JSONException {
        int actualCommentLength;

        if (comments.length() == 0) {
            return;
        }

        JSONObject more = comments.getJSONObject(comments.length() - 1).getJSONObject(JSONUtils.DATA_KEY);

        //Maybe moreChildrenFullnames contain only commentsJSONArray and no more info
        if (more.has(JSONUtils.COUNT_KEY)) {
            JSONArray childrenArray = more.getJSONArray(JSONUtils.CHILDREN_KEY);

            for (int i = 0; i < childrenArray.length(); i++) {
                moreChildrenFullnames.add("t1_" + childrenArray.getString(i));
            }

            actualCommentLength = comments.length() - 1;
        } else {
            actualCommentLength = comments.length();
        }

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
            Comment singleComment = parseSingleComment(data, depth, locale);

            if (data.get(JSONUtils.REPLIES_KEY) instanceof JSONObject) {
                JSONArray childrenArray = data.getJSONObject(JSONUtils.REPLIES_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                ArrayList<Comment> children = new ArrayList<>();
                ArrayList<String> nextMoreChildrenFullnames = new ArrayList<>();
                parseCommentRecursion(childrenArray, children, nextMoreChildrenFullnames, singleComment.getDepth(),
                        locale);
                singleComment.addChildren(children);
                singleComment.setMoreChildrenFullnames(nextMoreChildrenFullnames);
            }

            newCommentData.add(singleComment);
        }
    }

    private static void expandChildren(ArrayList<Comment> comments, ArrayList<Comment> visibleComments,
                                       boolean setExpanded) {
        for (Comment c : comments) {
            visibleComments.add(c);
            if (c.hasReply()) {
                if (setExpanded) {
                    c.setExpanded(true);
                }
                expandChildren(c.getChildren(), visibleComments, setExpanded);
            } else {
                c.setExpanded(true);
            }
            if (c.hasMoreChildrenFullnames() && c.getMoreChildrenFullnames().size() > c.getMoreChildrenStartingIndex()) {
                //Add a load more placeholder
                Comment placeholder = new Comment(c.getFullName(), c.getDepth() + 1);
                visibleComments.add(placeholder);
                c.addChild(placeholder, c.getChildren().size());
            }
        }
    }

    static Comment parseSingleComment(JSONObject singleCommentData, int depth, Locale locale) throws JSONException {
        String id = singleCommentData.getString(JSONUtils.ID_KEY);
        String fullName = singleCommentData.getString(JSONUtils.NAME_KEY);
        String author = singleCommentData.getString(JSONUtils.AUTHOR_KEY);
        StringBuilder authorFlairHTMLBuilder = new StringBuilder();
        if (singleCommentData.has(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY)) {
            JSONArray flairArray = singleCommentData.getJSONArray(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY);
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.getJSONObject(i);
                String e = flairObject.getString(JSONUtils.E_KEY);
                if (e.equals("text")) {
                    authorFlairHTMLBuilder.append(Html.escapeHtml(flairObject.getString(JSONUtils.T_KEY)));
                } else if (e.equals("emoji")) {
                    authorFlairHTMLBuilder.append("<img src=\"").append(Html.escapeHtml(flairObject.getString(JSONUtils.U_KEY))).append("\">");
                }
            }
        }
        String authorFlair = singleCommentData.isNull(JSONUtils.AUTHOR_FLAIR_TEXT_KEY) ? "" : singleCommentData.getString(JSONUtils.AUTHOR_FLAIR_TEXT_KEY);
        String linkAuthor = singleCommentData.has(JSONUtils.LINK_AUTHOR_KEY) ? singleCommentData.getString(JSONUtils.LINK_AUTHOR_KEY) : null;
        String linkId = singleCommentData.getString(JSONUtils.LINK_ID_KEY).substring(3);
        String subredditName = singleCommentData.getString(JSONUtils.SUBREDDIT_KEY);
        String parentId = singleCommentData.getString(JSONUtils.PARENT_ID_KEY);
        boolean isSubmitter = singleCommentData.getBoolean(JSONUtils.IS_SUBMITTER_KEY);
        String distinguished = singleCommentData.getString(JSONUtils.DISTINGUISHED_KEY);
        String commentMarkdown = "";
        if (!singleCommentData.isNull(JSONUtils.BODY_KEY)) {
            commentMarkdown = Utils.modifyMarkdown(singleCommentData.getString(JSONUtils.BODY_KEY).trim());
        }
        String commentRawText = Utils.trimTrailingWhitespace(
                Html.fromHtml(singleCommentData.getString(JSONUtils.BODY_HTML_KEY))).toString();
        String permalink = Html.fromHtml(singleCommentData.getString(JSONUtils.PERMALINK_KEY)).toString();
        StringBuilder awardingsBuilder = new StringBuilder();
        JSONArray awardingsArray = singleCommentData.getJSONArray(JSONUtils.ALL_AWARDINGS_KEY);
        for (int i = 0; i < awardingsArray.length(); i++) {
            JSONObject award = awardingsArray.getJSONObject(i);
            int count = award.getInt(JSONUtils.COUNT_KEY);
            JSONArray icons = award.getJSONArray(JSONUtils.RESIZED_ICONS_KEY);
            if (icons.length() > 4) {
                String iconUrl = icons.getJSONObject(3).getString(JSONUtils.URL_KEY);
                awardingsBuilder.append("<img src=\"").append(Html.escapeHtml(iconUrl)).append("\"> ").append("x").append(count).append(" ");
            } else if (icons.length() > 0) {
                String iconUrl = icons.getJSONObject(icons.length() - 1).getString(JSONUtils.URL_KEY);
                awardingsBuilder.append("<img src=\"").append(Html.escapeHtml(iconUrl)).append("\"> ").append("x").append(count).append(" ");
            }
        }
        int score = singleCommentData.getInt(JSONUtils.SCORE_KEY);
        int voteType;
        if (singleCommentData.isNull(JSONUtils.LIKES_KEY)) {
            voteType = VOTE_TYPE_NO_VOTE;
        } else {
            voteType = singleCommentData.getBoolean(JSONUtils.LIKES_KEY) ? VOTE_TYPE_UPVOTE : VOTE_TYPE_DOWNVOTE;
            score -= voteType;
        }
        long submitTime = singleCommentData.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
        boolean scoreHidden = singleCommentData.getBoolean(JSONUtils.SCORE_HIDDEN_KEY);
        boolean saved = singleCommentData.getBoolean(JSONUtils.SAVED_KEY);

        if (singleCommentData.has(JSONUtils.DEPTH_KEY)) {
            depth = singleCommentData.getInt(JSONUtils.DEPTH_KEY);
        }

        boolean collapsed = singleCommentData.getBoolean(JSONUtils.COLLAPSED_KEY);
        boolean hasReply = !(singleCommentData.get(JSONUtils.REPLIES_KEY) instanceof String);

        return new Comment(id, fullName, author, authorFlair, authorFlairHTMLBuilder.toString(),
                linkAuthor, submitTime, commentMarkdown, commentRawText,
                linkId, subredditName, parentId, score, voteType, isSubmitter, distinguished,
                permalink, awardingsBuilder.toString(),depth, collapsed, hasReply, scoreHidden, saved);
    }

    @Nullable
    private static String parseSentCommentErrorMessage(String response) {
        try {
            JSONObject responseObject = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY);

            if (responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() != 0) {
                JSONArray error = responseObject.getJSONArray(JSONUtils.ERRORS_KEY)
                        .getJSONArray(responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() - 1);
                if (error.length() != 0) {
                    String errorString;
                    if (error.length() >= 2) {
                        errorString = error.getString(1);
                    } else {
                        errorString = error.getString(0);
                    }
                    return errorString.substring(0, 1).toUpperCase() + errorString.substring(1);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public interface ParseCommentListener {
        void onParseCommentSuccess(ArrayList<Comment> expandedComments, String parentId,
                                   ArrayList<String> moreChildrenFullnames);

        void onParseCommentFailed();
    }

    interface ParseSentCommentListener {
        void onParseSentCommentSuccess(Comment comment);

        void onParseSentCommentFailed(@Nullable String errorMessage);
    }

    private static class ParseCommentAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONArray commentsJSONArray;
        private ArrayList<Comment> comments;
        private ArrayList<Comment> newComments;
        private ArrayList<Comment> expandedNewComments;
        private ArrayList<String> moreChildrenFullnames;
        private Locale locale;
        private String parentId;
        private int depth;
        private boolean expandChildren;
        private ParseCommentListener parseCommentListener;
        private boolean parseFailed;

        ParseCommentAsyncTask(JSONArray commentsJSONArray, ArrayList<Comment> comments, Locale locale,
                              @Nullable String parentId, int depth, boolean expandChildren,
                              ParseCommentListener parseCommentListener) {
            this.commentsJSONArray = commentsJSONArray;
            this.comments = comments;
            newComments = new ArrayList<>();
            expandedNewComments = new ArrayList<>();
            moreChildrenFullnames = new ArrayList<>();
            this.locale = locale;
            this.parentId = parentId;
            this.depth = depth;
            this.expandChildren = expandChildren;
            parseFailed = false;
            this.parseCommentListener = parseCommentListener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                parseCommentRecursion(commentsJSONArray, newComments, moreChildrenFullnames, depth, locale);
                expandChildren(newComments, expandedNewComments, expandChildren);
            } catch (JSONException e) {
                parseFailed = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed) {
                if (expandChildren) {
                    comments.addAll(expandedNewComments);
                } else {
                    comments.addAll(newComments);
                }
                parseCommentListener.onParseCommentSuccess(comments, parentId, moreChildrenFullnames);
            } else {
                parseCommentListener.onParseCommentFailed();
            }
        }
    }

    private static class ParseSentCommentAsyncTask extends AsyncTask<Void, Void, Void> {
        private String response;
        private int depth;
        private Locale locale;
        private ParseSentCommentListener parseSentCommentListener;
        private boolean parseFailed;
        private String errorMessage;
        private Comment comment;

        ParseSentCommentAsyncTask(String response, int depth, Locale locale, ParseSentCommentListener parseSentCommentListener) {
            this.response = response;
            this.depth = depth;
            this.locale = locale;
            this.parseSentCommentListener = parseSentCommentListener;
            parseFailed = false;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                JSONObject sentCommentData = new JSONObject(response);
                comment = parseSingleComment(sentCommentData, depth, locale);
            } catch (JSONException e) {
                e.printStackTrace();
                errorMessage = parseSentCommentErrorMessage(response);
                parseFailed = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (parseFailed) {
                parseSentCommentListener.onParseSentCommentFailed(errorMessage);
            } else {
                parseSentCommentListener.onParseSentCommentSuccess(comment);
            }
        }
    }
}

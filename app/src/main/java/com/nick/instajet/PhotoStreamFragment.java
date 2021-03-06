package com.nick.instajet;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link PhotoStreamFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link PhotoStreamFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PhotoStreamFragment extends Fragment implements InstagramApiHandlerTaskListener, AbsListView.OnScrollListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_USER_ID = "userId";
    private static final String ARG_STREAM_TYPE = "streamType";

    // TODO: Rename and change types of parameters
    private String userId;
    private String streamType;
    private String accessToken;

    private String nextUrl;
    private JSONArray mediaList;
    private PhotoStreamListAdapter mediaListAdapter;

    private volatile boolean loading;
    LinearLayout layoutLoadMore;

    private OnFragmentInteractionListener mListener;

    private static final String RECENT_FIRST_API_REQUEST_TEMPLATE = "https://api.instagram.com/v1/users/%1$s/media/recent?access_token=%2$s";
    private static final String RECENT_PAGINATED_API_REQUEST_TEMPLATE = "https://api.instagram.com/v1/users/%1$s/media/recent?access_token=%2$s&max_id=%3$s";

    public static final String STREAM_TYPE_USER = "user";
    public static final String STREAM_TYPE_FEED = "feed";
    public static final String STREAM_TYPE_POPULAR = "popular";

    private static final int THRESHOLD_LOAD_MORE = 3;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param userId Parameter 1.
     * @param streamType Parameter 2.
     * @return A new instance of fragment PhotoStreamFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static PhotoStreamFragment newInstance(String userId, String streamType) {
        PhotoStreamFragment fragment = new PhotoStreamFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        args.putString(ARG_STREAM_TYPE, streamType);
        fragment.setArguments(args);
        return fragment;
    }

    public PhotoStreamFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString(ARG_USER_ID);
            streamType = getArguments().getString(ARG_STREAM_TYPE);
        }
        accessToken = getActivity().getSharedPreferences("InstaJetPrefs", Context.MODE_PRIVATE).getString("accessToken", "notoken");
        mediaList = new JSONArray();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_photo_stream, container, false);
        return v;
    }

    @Override
    public void onViewCreated(View v, Bundle b) {
        super.onViewCreated(v, b);
        setupStream();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void setupStream() {
//        // set up show more button
//        buttonLoadMore = new Button(getActivity());
//        buttonLoadMore.setText("Load more");
//        buttonLoadMore.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                requestNextPhotoSet(nextUrl);
//            }
//        });
//        buttonLoadMore.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, ListView.LayoutParams.WRAP_CONTENT));
//
//        // setup load more progress bar
//        progressLoadMore = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleSmall);

        // setup the footer
        layoutLoadMore = (LinearLayout) LayoutInflater.from(getActivity()).inflate(R.layout.listfooter_photo_stream, null);
        setLoadMoreFooterVisibility(View.VISIBLE);

        ListView listViewPhotoStream = (ListView) getView().findViewById(R.id.ListViewPhotoStream);
        listViewPhotoStream.setOnScrollListener(this);

        String firstApiUrl = "";

        switch (streamType) {
            case STREAM_TYPE_USER :
                firstApiUrl = String.format(RECENT_FIRST_API_REQUEST_TEMPLATE, userId, accessToken);
                break;

            default :
                Log.e("asd", "invalid stream type");

        }

        requestNextPhotoSet(firstApiUrl);
    }

    private void requestNextPhotoSet(String nextUrl) {
        if (nextUrl == null) {
            return;
        }

        setProgressBarVisibility(View.VISIBLE);
        loading = true;

        Log.e("asd", nextUrl);

        new InstagramApiHandlerTask(this).execute(nextUrl);
    }

    private void onReceiveNextPhotoSet(JSONObject response) {
        Log.e("asd", response.toString());

        setProgressBarVisibility(View.GONE);
        loading = false;

        try {
            JSONObject meta = response.getJSONObject("meta");
            int responseCode = meta.getInt("code");
            if (responseCode == 200) {
                // all ok
                populateStream(response);

            } else if (responseCode == 400) {
                // permission error here
                setRestrictedWarningVisibility(View.VISIBLE);

            } else {
                Log.e("asd", "unknown errror: " + responseCode);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void populateStream(JSONObject response) {
        try {

            // settle pagination
            JSONObject pagination = response.getJSONObject("pagination");
            String nextUrlTemp = pagination.optString("next_url", null);
            if (nextUrlTemp == null) {
                Log.e("asd", "no more url");
                setLoadMoreFooterVisibility(View.GONE);
                nextUrl = null;
            } else {
                Log.e("asd", "have more url");
                nextUrl = pagination.getString("next_url");
            }

            // update the main medialist
            JSONArray mediaListUpdates = response.getJSONArray("data");
            for (int i = 0; i < mediaListUpdates.length(); i++) {
                mediaList.put(mediaListUpdates.getJSONObject(i));
            }

            // set the adapter if none, else call update on it
            if (mediaListAdapter == null) {
                mediaListAdapter = new PhotoStreamListAdapter(getActivity(), mediaList);
                ListView listViewPhotoStream = (ListView) getView().findViewById(R.id.ListViewPhotoStream);
                listViewPhotoStream.setAdapter(mediaListAdapter);
            } else {
                mediaListAdapter.notifyDataSetChanged();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("asd", "jsondied");
        }
    }




    private void showRestrictedWarning(View v) {
        LinearLayout layoutRestrictedWarning = (LinearLayout) v.findViewById(R.id.LayoutRestrictedWarning);
        layoutRestrictedWarning.setVisibility(View.VISIBLE);
    }

    @Override
    public void receiveApiResponse(JSONObject j) {
        if (getActivity() == null) {
            return;
        }

        if (j == null) {
            Log.e("asd", "j is null");
        } else {
            onReceiveNextPhotoSet(j);
        }
    }

    private void makeToast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
    }

    private void setLoadMoreFooterVisibility(int visibility) {
        if (getView() == null) {
            return;
        }
        ListView listViewPhotoStream = (ListView) getView().findViewById(R.id.ListViewPhotoStream);
        if (visibility == View.GONE) {
            listViewPhotoStream.removeFooterView(layoutLoadMore);
        } else if (visibility == View.VISIBLE) {
            listViewPhotoStream.addFooterView(layoutLoadMore);
        }
    }

    private void setProgressBarVisibility(int visibility) {
        if (getView() == null) {
            return;
        }
        ProgressBar progressBarLoadProgress = (ProgressBar) getView().findViewById(R.id.ProgressBarLoadProgress);
        progressBarLoadProgress.setVisibility(visibility);
    }

    private void setRestrictedWarningVisibility(int visibility) {
        if (getView() == null) {
            return;
        }
        LinearLayout layoutRestrictedWarning = (LinearLayout) getView().findViewById(R.id.LayoutRestrictedWarning);
        layoutRestrictedWarning.setVisibility(visibility);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (nextUrl == null) {
            Log.e("asd", "end of stream");
            return;
        }
        if (getView() == null) {
            return;
        }
        if (loading) {
            Log.e("asd", "WAIT still loading");
            return;
        }
        ListView listViewPhotoStream = (ListView) getView().findViewById(R.id.ListViewPhotoStream);
        if (scrollState == SCROLL_STATE_IDLE) {
            if (listViewPhotoStream.getLastVisiblePosition() >= listViewPhotoStream.getCount() - 1 - THRESHOLD_LOAD_MORE) {
                //load more list items:
                Log.e("asd", "loading next set");
                requestNextPhotoSet(nextUrl);
            }
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        // empty
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

}

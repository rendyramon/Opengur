package com.kenny.openimgur.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;

import com.kenny.openimgur.R;
import com.kenny.openimgur.SettingsActivity;
import com.kenny.openimgur.ViewActivity;
import com.kenny.openimgur.adapters.GalleryAdapter;
import com.kenny.openimgur.api.ApiClient;
import com.kenny.openimgur.api.Endpoints;
import com.kenny.openimgur.api.ImgurBusEvent;
import com.kenny.openimgur.classes.ImgurAlbum;
import com.kenny.openimgur.classes.ImgurBaseObject;
import com.kenny.openimgur.classes.ImgurHandler;
import com.kenny.openimgur.classes.ImgurPhoto;
import com.kenny.openimgur.classes.OpenImgurApp;
import com.kenny.openimgur.classes.TabActivityListener;
import com.kenny.openimgur.ui.FilterDialogFragment;
import com.kenny.openimgur.ui.HeaderGridView;
import com.kenny.openimgur.ui.MultiStateView;
import com.kenny.openimgur.util.ViewUtils;
import com.nostra13.universalimageloader.core.listener.PauseOnScrollListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.util.ThrowableFailureEvent;

/**
 * Created by kcampagna on 8/14/14.
 */
public class GalleryFragment extends Fragment implements FilterDialogFragment.FilterListener {
    private static final String KEY_SECTION = "section";

    private static final String KEY_SORT = "sort";

    private static final String KEY_CURRENT_POSITION = "position";

    private static final String KEY_ITEMS = "items";

    private static final String KEY_QUALITY = "quality";

    private static final String KEY_CURRENT_PAGE = "page";

    private static final int PAGE = 0;

    public enum GallerySection {
        HOT("hot"),
        USER("user"),
        TOP("top");

        private final String mSection;

        private GallerySection(String s) {
            mSection = s;
        }

        public String getSection() {
            return mSection;
        }

        /**
         * Returns the Enum value for the section based on the string
         *
         * @param section
         * @return
         */
        public static GallerySection getSectionFromString(String section) {
            return HOT.getSection().equals(section) ? HOT : USER;
        }

        /**
         * Returns the position in the enum array of the given section
         *
         * @param section
         * @return
         */
        public static int getPositionFromSeection(GallerySection section) {
            GallerySection[] sections = GallerySection.values();

            for (int i = 0; i < sections.length; i++) {
                if (section.equals(sections[i])) {
                    return i;
                }
            }

            return 0;
        }

        /**
         * Returns the GallerySection in the enum array from the given position
         *
         * @param position
         * @return
         */
        public static GallerySection getSectionFromPosition(int position) {
            GallerySection[] sections = GallerySection.values();

            for (int i = 0; i < sections.length; i++) {
                if (sections[i] == sections[position]) {
                    return sections[i];
                }
            }

            return GallerySection.HOT;
        }

        /**
         * Returns the String Resource for the section
         *
         * @return
         */
        @StringRes
        public int getResourceId() {
            switch (this) {
                case HOT:
                    return R.string.viral;

                case TOP:
                    return R.string.top_score;

                case USER:
                    return R.string.user_sub;
            }

            return R.string.viral;
        }
    }

    public enum GallerySort {
        TIME("time"),
        VIRAL("viral");

        private final String mSort;

        private GallerySort(String s) {
            mSort = s;
        }

        public String getSort() {
            return mSort;
        }

        /**
         * Returns the Enum value based on a string
         *
         * @param sort
         * @return
         */
        public static GallerySort getSortFromString(String sort) {
            if (TIME.getSort().equals(sort)) {
                return TIME;
            }

            return VIRAL;
        }

        /**
         * Returns a string array for the popup dialog for choosing filter options
         *
         * @param context
         * @return
         */
        public static String[] getPopupArray(Context context) {
            GallerySort[] items = GallerySort.values();
            String[] array = new String[items.length];
            for (int i = 0; i < items.length; i++) {
                array[i] = context.getString(getStringId(items[i]));
            }

            return array;
        }

        /**
         * Returns the string id for the corresponding GallerySort enum
         *
         * @param sort
         * @return
         */
        public static int getStringId(GallerySort sort) {
            switch (sort) {
                case TIME:
                    return R.string.filter_time;

                case VIRAL:
                default:
                    return R.string.filter_popular;
            }
        }
    }

    private GallerySection mSection = GallerySection.HOT;

    private GallerySort mSort = GallerySort.TIME;

    private int mCurrentPage = 0;

    private MultiStateView mMultiView;

    private HeaderGridView mGridView;

    private GalleryAdapter mAdapter;

    private TabActivityListener mListener;

    private ApiClient mApiClient;

    private boolean mIsLoading = false;

    private int mPreviousItem = 0;

    private String mQuality;

    public static GalleryFragment createInstance() {
        return new GalleryFragment();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity instanceof TabActivityListener) {
            mListener = (TabActivityListener) activity;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);

        if (mAdapter == null || mAdapter.isEmpty()) {
            mMultiView.setViewState(MultiStateView.ViewState.LOADING);
            mIsLoading = true;

            if (mListener != null) {
                mListener.onLoadingStarted(PAGE);
            }

            getGallery();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        mMultiView = null;
        mGridView = null;
        mHandler.removeCallbacksAndMessages(null);

        if (mAdapter != null) {
            mAdapter.clear();
            mAdapter = null;
        }

        SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
        edit.putString("section", mSection.getSection());
        edit.putString("sort", mSort.getSort()).apply();
        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMultiView = (MultiStateView) view.findViewById(R.id.multiStateView);
        mGridView = (HeaderGridView) mMultiView.findViewById(R.id.grid);
        mGridView.setOnScrollListener(new PauseOnScrollListener(OpenImgurApp.getInstance(getActivity()).getImageLoader(), false, true,
                new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {

                    }

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                        // Hide the actionbar when scrolling down, show when scrolling up
                        if (firstVisibleItem > mPreviousItem && mListener != null) {
                            mListener.onHideActionBar(false);
                        } else if (firstVisibleItem < mPreviousItem && mListener != null) {
                            mListener.onHideActionBar(true);
                        }

                        mPreviousItem = firstVisibleItem;

                        // Load more items when hey get to the end of the list
                        if (totalItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount && !mIsLoading) {
                            mIsLoading = true;
                            mCurrentPage++;
                            getGallery();
                        }
                    }
                }));
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                int headerSize = mGridView.getNumColumns() * mGridView.getHeaderViewCount();
                int adapterPosition = position - headerSize;
                // Don't respond to the header being clicked

                if (adapterPosition >= 0) {
                    ImgurBaseObject[] items = mAdapter.getItems(adapterPosition);
                    int itemPosition = adapterPosition;

                    // Get the correct array index of the selected item
                    if (itemPosition > GalleryAdapter.MAX_ITEMS / 2) {
                        itemPosition = items.length == GalleryAdapter.MAX_ITEMS ? GalleryAdapter.MAX_ITEMS / 2 :
                                items.length - (mAdapter.getCount() - itemPosition);
                    }

                    startActivity(ViewActivity.createIntent(getActivity(), items, itemPosition));
                }
            }
        });

        handleBundle(savedInstanceState);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser && mMultiView != null &&
                mMultiView.getViewState() != MultiStateView.ViewState.ERROR && mListener != null) {
            mListener.onLoadingComplete(PAGE);
        }
    }

    private void handleBundle(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            mQuality = pref.getString(SettingsActivity.THUMBNAIL_QUALITY_KEY, SettingsActivity.THUMBNAIL_QUALITY_LOW);
            mSort = GallerySort.getSortFromString(pref.getString("sort", null));
            mSection = GallerySection.getSectionFromString(pref.getString("section", null));
        } else {
            mSort = GallerySort.getSortFromString(savedInstanceState.getString(KEY_SORT, GallerySort.TIME.getSort()));
            mSection = GallerySection.getSectionFromString(savedInstanceState.getString(KEY_SECTION, GallerySection.HOT.getSection()));
            mQuality = savedInstanceState.getString(KEY_QUALITY, SettingsActivity.THUMBNAIL_QUALITY_LOW);
            mCurrentPage = savedInstanceState.getInt(KEY_CURRENT_PAGE, 0);

            if (savedInstanceState.containsKey(KEY_ITEMS)) {
                ImgurBaseObject[] items = (ImgurBaseObject[]) savedInstanceState.getParcelableArray(KEY_ITEMS);
                int currentPosition = savedInstanceState.getInt(KEY_CURRENT_POSITION, 0);
                mAdapter = new GalleryAdapter(getActivity(), new ArrayList<ImgurBaseObject>(Arrays.asList(items)), mQuality);
                mGridView.addHeaderView(ViewUtils.getHeaderViewForTranslucentStyle(getActivity(), 0));
                mGridView.setAdapter(mAdapter);
                mGridView.setSelection(currentPosition);

                if (mListener != null) {
                    mListener.onLoadingComplete(PAGE);
                }

                mMultiView.setViewState(MultiStateView.ViewState.CONTENT);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.gallery, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                mCurrentPage = 0;
                mIsLoading = true;

                if (mAdapter != null) {
                    mAdapter.clear();
                    mAdapter.notifyDataSetChanged();
                }

                if (mListener != null) {
                    mListener.onLoadingStarted(PAGE);
                }

                mMultiView.setViewState(MultiStateView.ViewState.LOADING);
                getGallery();
                return true;

            case R.id.filter:
                FilterDialogFragment filter = FilterDialogFragment.createInstance(mSort, mSection);
                filter.setFilterListner(this);
                filter.show(getFragmentManager(), "filter");
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Returns the URL based on the selected sort and section
     *
     * @return
     */
    private String getGalleryUrl() {
        return String.format(Endpoints.GALLERY.getUrl(), mSection.getSection(),
                mSort.getSort(), mCurrentPage);
    }

    /**
     * Queries the Api for Gallery items
     */
    private void getGallery() {
        if (mApiClient == null) {
            mApiClient = new ApiClient(getGalleryUrl(), ApiClient.HttpRequest.GET);
        } else {
            mApiClient.setUrl(getGalleryUrl());
        }

        mApiClient.doWork(ImgurBusEvent.EventType.GALLERY, mSection.getSection(), null);
    }

    /**
     * Event Method that receives events from the Bus
     *
     * @param event
     */
    public void onEventAsync(@NonNull ImgurBusEvent event) {
        if (event.eventType == ImgurBusEvent.EventType.GALLERY && mSection.getSection().equals(event.id)) {
            try {
                int statusCode = event.json.getInt(ApiClient.KEY_STATUS);
                List<ImgurBaseObject> objects = null;

                if (statusCode == ApiClient.STATUS_OK) {
                    JSONArray arr = event.json.getJSONArray(ApiClient.KEY_DATA);
                    objects = new ArrayList<ImgurBaseObject>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);

                        if (item.has("is_album") && item.getBoolean("is_album")) {
                            ImgurAlbum a = new ImgurAlbum(item);
                            objects.add(a);
                        } else {
                            ImgurPhoto p = new ImgurPhoto(item);
                            objects.add(p);
                        }
                    }

                    mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_COMPLETE, objects);
                } else {
                    mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_FAILED, ApiClient.getErrorCodeStringResource(statusCode));
                }

            } catch (JSONException e) {
                e.printStackTrace();
                mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_FAILED, ApiClient.getErrorCodeStringResource(ApiClient.STATUS_JSON_EXCEPTION));
            }
        }
    }

    /**
     * Event Method that is fired if EventBus throws an error
     *
     * @param event
     */
    public void onEventMainThread(ThrowableFailureEvent event) {
        Throwable e = event.getThrowable();

        if (e instanceof IOException) {
            mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_FAILED, ApiClient.getErrorCodeStringResource(ApiClient.STATUS_IO_EXCEPTION));
        } else if (e instanceof JSONException) {
            mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_FAILED, ApiClient.getErrorCodeStringResource(ApiClient.STATUS_JSON_EXCEPTION));
        } else {
            mHandler.sendMessage(ImgurHandler.MESSAGE_ACTION_FAILED, ApiClient.getErrorCodeStringResource(ApiClient.STATUS_INTERNAL_ERROR));
        }

        event.getThrowable().printStackTrace();
    }

    @Override
    public void onFilterChange(GallerySection section, GallerySort sort) {
        if (mAdapter != null) {
            mAdapter.clear();
            mAdapter.notifyDataSetChanged();
        }

        mSection = section;
        mSort = sort;
        mCurrentPage = 0;
        mIsLoading = true;
        mMultiView.setViewState(MultiStateView.ViewState.LOADING);
        getGallery();
    }

    private ImgurHandler mHandler = new ImgurHandler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_ACTION_COMPLETE:
                    List<ImgurBaseObject> gallery = (List<ImgurBaseObject>) msg.obj;

                    if (mAdapter == null) {
                        mAdapter = new GalleryAdapter(getActivity(), gallery, mQuality);
                        mGridView.addHeaderView(ViewUtils.getHeaderViewForTranslucentStyle(getActivity(), 0));
                        mGridView.setAdapter(mAdapter);
                    } else {
                        mAdapter.addItems(gallery);
                        mAdapter.notifyDataSetChanged();
                    }

                    if (mListener != null) {
                        mListener.onLoadingComplete(PAGE);
                    }


                    mMultiView.setViewState(MultiStateView.ViewState.CONTENT);

                    // Due to MultiStateView setting the views visibility to GONE, the list will not reset to the top
                    // If they change the filter or refresh
                    if (mCurrentPage == 0) {
                        mMultiView.post(new Runnable() {
                            @Override
                            public void run() {
                                mGridView.setSelection(0);
                            }
                        });
                    }
                    break;

                case MESSAGE_ACTION_FAILED:
                    if (mAdapter == null || mAdapter.isEmpty()) {
                        if (mListener != null) {
                            mListener.onError((Integer) msg.obj, PAGE);
                        }

                        mMultiView.setErrorText(R.id.errorMessage, (Integer) msg.obj);
                        mMultiView.setViewState(MultiStateView.ViewState.ERROR);
                    }

                    break;

                default:
                    if (mListener != null) {
                        mListener.onLoadingComplete(PAGE);
                    }

                    super.handleMessage(msg);
                    break;
            }

            mIsLoading = false;
        }
    };

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_SECTION, mSection.getSection());
        outState.putString(KEY_SORT, mSort.getSort());
        outState.putString(KEY_QUALITY, mQuality);
        outState.putInt(KEY_CURRENT_PAGE, mCurrentPage);

        if (mAdapter != null && !mAdapter.isEmpty()) {
            ImgurBaseObject[] objects = new ImgurBaseObject[mAdapter.getCount()];
            System.arraycopy(mAdapter.getAllItems(), 0, objects, 0, objects.length);
            outState.putParcelableArray(KEY_ITEMS, objects);
            outState.putInt(KEY_CURRENT_POSITION, mGridView.getFirstVisiblePosition());
        }

        super.onSaveInstanceState(outState);
    }
}

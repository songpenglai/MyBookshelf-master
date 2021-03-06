package com.monke.monkeybook.presenter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;
import com.monke.basemvplib.BasePresenterImpl;
import com.monke.basemvplib.impl.IView;
import com.monke.monkeybook.base.observer.SimpleObserver;
import com.monke.monkeybook.bean.SearchBookBean;
import com.monke.monkeybook.bean.SearchHistoryBean;
import com.monke.monkeybook.dao.DbHelper;
import com.monke.monkeybook.dao.SearchHistoryBeanDao;
import com.monke.monkeybook.help.RxBusTag;
import com.monke.monkeybook.model.SearchBookModel;
import com.monke.monkeybook.presenter.contract.SearchBookContract;
import com.monke.monkeybook.utils.NetworkUtil;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SearchBookPresenterImpl extends BasePresenterImpl<SearchBookContract.View> implements SearchBookContract.Presenter {
    private static final int BOOK = 2;

    private String searchKey;
    private SearchBookModel searchBookModel;

    public SearchBookPresenterImpl(Context context, boolean useMy716) {
        //搜索监听
        SearchBookModel.OnSearchListener onSearchListener = new SearchBookModel.OnSearchListener() {

            @Override
            public void searchSourceEmpty() {
                Toast.makeText(mView.getContext(), "没有选中任何书源", Toast.LENGTH_SHORT).show();
                mView.refreshFinish();
            }

            @Override
            public void resetSearchBook() {
                mView.resetSearchBook();
            }

            @Override
            public void searchBookFinish() {
                mView.refreshFinish();
            }

            @Override
            public boolean checkExists(SearchBookBean searchBook) {
                return false;//这里不用检查
            }

            @Override
            public void loadMoreSearchBook(List<SearchBookBean> searchBookBeanList) {
                mView.loadMoreSearchBook(searchBookBeanList);
            }

            @Override
            public void searchBookError() {
                mView.searchBookError();
            }

            @Override
            public int getItemCount() {
                return mView.getSearchBookAdapter().getICount();
            }
        };
        //搜索引擎初始化
        searchBookModel = new SearchBookModel(context, onSearchListener, useMy716);
    }

    @Override
    public void insertSearchHistory() {
        final int type = SearchBookPresenterImpl.BOOK;
        final String content = mView.getEdtContent().getText().toString().trim();
        Observable.create((ObservableOnSubscribe<SearchHistoryBean>) e -> {
            List<SearchHistoryBean> data = DbHelper.getInstance().getmDaoSession().getSearchHistoryBeanDao()
                    .queryBuilder()
                    .where(SearchHistoryBeanDao.Properties.Type.eq(type), SearchHistoryBeanDao.Properties.Content.eq(content))
                    .limit(1)
                    .build().list();
            SearchHistoryBean searchHistoryBean;
            if (null != data && data.size() > 0) {
                searchHistoryBean = data.get(0);
                searchHistoryBean.setDate(System.currentTimeMillis());
                DbHelper.getInstance().getmDaoSession().getSearchHistoryBeanDao().update(searchHistoryBean);
            } else {
                searchHistoryBean = new SearchHistoryBean(type, content, System.currentTimeMillis());
                DbHelper.getInstance().getmDaoSession().getSearchHistoryBeanDao().insert(searchHistoryBean);
            }
            e.onNext(searchHistoryBean);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<SearchHistoryBean>() {
                    @Override
                    public void onNext(SearchHistoryBean value) {
                        mView.insertSearchHistorySuccess(value);
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public void cleanSearchHistory() {
        final String content = mView.getEdtContent().getText().toString().trim();
        Observable.create((ObservableOnSubscribe<Integer>) e -> {
            int a = DbHelper.getInstance().getDb().delete(SearchHistoryBeanDao.TABLENAME,
                    SearchHistoryBeanDao.Properties.Type.columnName + "=? and " + SearchHistoryBeanDao.Properties.Content.columnName + " like ?",
                    new String[]{String.valueOf(SearchBookPresenterImpl.BOOK), "%" + content + "%"});
            e.onNext(a);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Integer>() {
                    @Override
                    public void onNext(Integer value) {
                        if (value > 0) {
                            mView.querySearchHistorySuccess(null);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public void cleanSearchHistory(SearchHistoryBean searchHistoryBean) {
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            DbHelper.getInstance().getmDaoSession().getSearchHistoryBeanDao().delete(searchHistoryBean);
            e.onNext(true);
            e.onComplete();
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Boolean>() {
                    @Override
                    public void onNext(Boolean value) {
                        if (value) {
                            querySearchHistory(mView.getEdtContent().getText().toString().trim());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public void querySearchHistory(String query) {
        Observable.create((ObservableOnSubscribe<List<SearchHistoryBean>>) e -> {
            List<SearchHistoryBean> data = DbHelper.getInstance().getmDaoSession().getSearchHistoryBeanDao()
                    .queryBuilder()
                    .where(SearchHistoryBeanDao.Properties.Type.eq(SearchBookPresenterImpl.BOOK), SearchHistoryBeanDao.Properties.Content.like("%" + query + "%"))
                    .orderDesc(SearchHistoryBeanDao.Properties.Date)
                    .limit(20)
                    .build().list();
            e.onNext(data);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<List<SearchHistoryBean>>() {
                    @Override
                    public void onNext(List<SearchHistoryBean> value) {
                        if (null != value)
                            mView.querySearchHistorySuccess(value);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
    }

    @Override
    public void setUseMy716(boolean useMy716) {
        searchBookModel.setUseMy716(useMy716);
    }

    @Override
    public void toSearchBooks(String key) {
        if (!NetworkUtil.isNetworkAvailable()) {
            Toast.makeText(mView.getContext(), "无网络，搜索失败", Toast.LENGTH_SHORT).show();
            mView.refreshFinish();
            return;
        }

        int id = (int) System.currentTimeMillis();
        if (key == null) {
            searchBookModel.startSearch(id, searchKey);
        } else {
            searchKey = key;
            searchBookModel.startSearch(id, key);
        }
    }

    @Override
    public void stopSearch() {
        searchBookModel.stopSearch();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void attachView(@NonNull IView iView) {
        super.attachView(iView);
        RxBus.get().register(this);
    }

    @Override
    public void detachView() {
        searchBookModel.shutdownSearch();
        RxBus.get().unregister(this);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.SEARCH_BOOK)})
    public void searchBook(String searchKey) {
        mView.searchBook(searchKey);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.SOURCE_LIST_CHANGE)})
    public void sourceListChange(Boolean change) {
        searchBookModel.setSearchEngineChanged();
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.GET_ZFB_Hb)})
    public void getZfbHB(Boolean getZfbHB) {
        searchBookModel.setUseMy716(getZfbHB);
        mView.upMenu();
    }
}

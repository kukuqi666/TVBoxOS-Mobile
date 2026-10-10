package com.kukuqi.tvbox.osc.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.kukuqi.tvbox.osc.cache.Cache;
import com.kukuqi.tvbox.osc.cache.CacheDao;
import com.kukuqi.tvbox.osc.cache.VodCollect;
import com.kukuqi.tvbox.osc.cache.VodCollectDao;
import com.kukuqi.tvbox.osc.cache.VodRecord;
import com.kukuqi.tvbox.osc.cache.VodRecordDao;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Database(entities = {Cache.class, VodRecord.class, VodCollect.class}, version = 1)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();
}

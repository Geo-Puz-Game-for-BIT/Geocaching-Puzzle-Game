package com.bitse.geotreasure.di

import android.content.Context
import androidx.room.Room
import com.bitse.geotreasure.core.LocationProvider
import com.bitse.geotreasure.data.local.CheckInRecordDao
import com.bitse.geotreasure.data.local.GeoTreasureDatabase
import com.bitse.geotreasure.data.location.DemoLocationProvider
import com.bitse.geotreasure.data.repository.DemoGeoTreasureRepository
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GeoTreasureDatabase =
        Room.databaseBuilder(context, GeoTreasureDatabase::class.java, "geotreasure.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCheckInDao(db: GeoTreasureDatabase): CheckInRecordDao = db.checkInDao()

    /**
     * 换后端时只改这一行：把 DemoGeoTreasureRepository 换成 CloudBaseGeoTreasureRepository，
     * 所有 ViewModel / 页面无需改动（都依赖 GeoTreasureRepository 接口）。
     */
    @Provides
    @Singleton
    fun provideRepository(dao: CheckInRecordDao): GeoTreasureRepository =
        DemoGeoTreasureRepository(dao)

    /**
     * 定位同样只换这一行：DemoLocationProvider → AMapLocationProvider。
     * 页面只依赖 LocationProvider 接口，拿到的就是一个「距目标 X 米」的 Flow。
     */
    @Provides
    @Singleton
    fun provideLocationProvider(): LocationProvider = DemoLocationProvider()
}

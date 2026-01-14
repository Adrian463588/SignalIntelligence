// di/AppModule.kt
package com.example.signalint.di

import android.content.Context
import com.example.signalint.data.AppDatabase
import com.example.signalint.data.ble.BleDao
import com.example.signalint.data.signal.SignalDao
import com.example.signalint.manager.*
import com.example.signalint.repository.BleRepository
import com.example.signalint.repository.BleRepositoryImpl
import com.example.signalint.repository.SignalRepository
import com.example.signalint.repository.SignalRepositoryImpl
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBleDao(database: AppDatabase): BleDao = database.bleDao()

    @Provides
    @Singleton
    fun provideSignalDao(database: AppDatabase): SignalDao = database.signalDao()

    @Provides
    @Singleton
    fun provideBleRepository(bleDao: BleDao): BleRepository {
        return BleRepositoryImpl(bleDao)
    }

    @Provides
    @Singleton
    fun provideSignalRepository(signalDao: SignalDao): SignalRepository {
        return SignalRepositoryImpl(signalDao)
    }

    @Provides
    fun provideBleScannerManager(
        @ApplicationContext context: Context
    ): BleScannerManager = BleScannerManager(context)

    @Provides
    fun provideBleGattManager(
        @ApplicationContext context: Context
    ): BleGattManager = BleGattManager(context)

    @Provides
    fun provideWifiScannerManager(
        @ApplicationContext context: Context
    ): WifiScannerManager = WifiScannerManager(context)
}

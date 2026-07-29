package com.agent.chat.di

import android.content.Context
import androidx.room.Room
import com.agent.chat.data.local.AgentChatDatabase
import com.agent.chat.data.local.MIGRATION_7_8
import com.agent.chat.data.local.MIGRATION_8_9
import com.agent.chat.data.local.MIGRATION_9_10
import com.agent.chat.data.local.MIGRATION_10_11
import com.agent.chat.data.local.MIGRATION_11_12
import com.agent.chat.data.local.dao.ConversationDao
import com.agent.chat.data.local.dao.MemoryDao
import com.agent.chat.data.local.dao.MessageDao
import com.agent.chat.data.local.dao.PersonaDao
import com.agent.chat.data.local.dao.ProviderConfigDao
import com.agent.chat.data.provider.AIProvider
import com.agent.chat.data.provider.AIProviderFactory
import com.agent.chat.data.provider.OpenAICompatibleProvider
import com.agent.chat.data.provider.network.OpenAIApi
import com.agent.chat.data.ai.response.HeuristicResponseEvaluator
import com.agent.chat.data.ai.response.ResponseEvaluator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderBindModule {

    @Binds
    @Singleton
    abstract fun bindAIProvider(impl: AIProviderFactory): AIProvider

    @Binds
    @Singleton
    abstract fun bindResponseEvaluator(impl: HeuristicResponseEvaluator): ResponseEvaluator
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AgentChatDatabase = Room.databaseBuilder(
        context,
        AgentChatDatabase::class.java,
        "agent_chat.db",
    ).addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideConversationDao(database: AgentChatDatabase): ConversationDao =
        database.conversationDao()

    @Provides
    fun provideMessageDao(database: AgentChatDatabase): MessageDao =
        database.messageDao()

    @Provides
    fun providePersonaDao(database: AgentChatDatabase): PersonaDao =
        database.personaDao()

    @Provides
    fun provideProviderConfigDao(database: AgentChatDatabase): ProviderConfigDao =
        database.providerConfigDao()

    @Provides
    fun provideMemoryDao(database: AgentChatDatabase): MemoryDao =
        database.memoryDao()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(com.agent.chat.data.provider.network.ChatMessageJsonAdapter.FACTORY)
        .add(com.agent.chat.data.provider.network.ObjectJsonAdapter.FACTORY)
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): OpenAIApi {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAIApi::class.java)
    }
}

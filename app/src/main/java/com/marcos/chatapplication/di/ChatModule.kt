package com.marcos.chatapplication.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.marcos.chatapplication.data.repository.ChatRepositoryImpl
import com.marcos.chatapplication.domain.contracts.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth
    ): ChatRepository { 
        return ChatRepositoryImpl(firestore, firebaseAuth)
    }
}
package com.voicelock.app.admin;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class LockManager_Factory implements Factory<LockManager> {
  private final Provider<Context> contextProvider;

  public LockManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LockManager get() {
    return newInstance(contextProvider.get());
  }

  public static LockManager_Factory create(Provider<Context> contextProvider) {
    return new LockManager_Factory(contextProvider);
  }

  public static LockManager newInstance(Context context) {
    return new LockManager(context);
  }
}

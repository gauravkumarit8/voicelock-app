package com.voicelock.app.data;

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
public final class OnboardingStatusStore_Factory implements Factory<OnboardingStatusStore> {
  private final Provider<Context> contextProvider;

  public OnboardingStatusStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public OnboardingStatusStore get() {
    return newInstance(contextProvider.get());
  }

  public static OnboardingStatusStore_Factory create(Provider<Context> contextProvider) {
    return new OnboardingStatusStore_Factory(contextProvider);
  }

  public static OnboardingStatusStore newInstance(Context context) {
    return new OnboardingStatusStore(context);
  }
}

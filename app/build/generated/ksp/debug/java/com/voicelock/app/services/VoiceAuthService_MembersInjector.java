package com.voicelock.app.services;

import com.voicelock.app.admin.LockManager;
import com.voicelock.app.data.OnboardingStatusStore;
import com.voicelock.app.data.VoiceprintStore;
import com.voicelock.app.ml.SpeakerVerificationEngine;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class VoiceAuthService_MembersInjector implements MembersInjector<VoiceAuthService> {
  private final Provider<SpeakerVerificationEngine> speakerVerificationEngineProvider;

  private final Provider<VoiceprintStore> voiceprintStoreProvider;

  private final Provider<OnboardingStatusStore> onboardingStatusStoreProvider;

  private final Provider<LockManager> lockManagerProvider;

  public VoiceAuthService_MembersInjector(
      Provider<SpeakerVerificationEngine> speakerVerificationEngineProvider,
      Provider<VoiceprintStore> voiceprintStoreProvider,
      Provider<OnboardingStatusStore> onboardingStatusStoreProvider,
      Provider<LockManager> lockManagerProvider) {
    this.speakerVerificationEngineProvider = speakerVerificationEngineProvider;
    this.voiceprintStoreProvider = voiceprintStoreProvider;
    this.onboardingStatusStoreProvider = onboardingStatusStoreProvider;
    this.lockManagerProvider = lockManagerProvider;
  }

  public static MembersInjector<VoiceAuthService> create(
      Provider<SpeakerVerificationEngine> speakerVerificationEngineProvider,
      Provider<VoiceprintStore> voiceprintStoreProvider,
      Provider<OnboardingStatusStore> onboardingStatusStoreProvider,
      Provider<LockManager> lockManagerProvider) {
    return new VoiceAuthService_MembersInjector(speakerVerificationEngineProvider, voiceprintStoreProvider, onboardingStatusStoreProvider, lockManagerProvider);
  }

  @Override
  public void injectMembers(VoiceAuthService instance) {
    injectSpeakerVerificationEngine(instance, speakerVerificationEngineProvider.get());
    injectVoiceprintStore(instance, voiceprintStoreProvider.get());
    injectOnboardingStatusStore(instance, onboardingStatusStoreProvider.get());
    injectLockManager(instance, lockManagerProvider.get());
  }

  @InjectedFieldSignature("com.voicelock.app.services.VoiceAuthService.speakerVerificationEngine")
  public static void injectSpeakerVerificationEngine(VoiceAuthService instance,
      SpeakerVerificationEngine speakerVerificationEngine) {
    instance.speakerVerificationEngine = speakerVerificationEngine;
  }

  @InjectedFieldSignature("com.voicelock.app.services.VoiceAuthService.voiceprintStore")
  public static void injectVoiceprintStore(VoiceAuthService instance,
      VoiceprintStore voiceprintStore) {
    instance.voiceprintStore = voiceprintStore;
  }

  @InjectedFieldSignature("com.voicelock.app.services.VoiceAuthService.onboardingStatusStore")
  public static void injectOnboardingStatusStore(VoiceAuthService instance,
      OnboardingStatusStore onboardingStatusStore) {
    instance.onboardingStatusStore = onboardingStatusStore;
  }

  @InjectedFieldSignature("com.voicelock.app.services.VoiceAuthService.lockManager")
  public static void injectLockManager(VoiceAuthService instance, LockManager lockManager) {
    instance.lockManager = lockManager;
  }
}

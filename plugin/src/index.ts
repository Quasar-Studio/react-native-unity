import {
  AndroidConfig,
  withGradleProperties,
  withProjectBuildGradle,
  withSettingsGradle,
  withStringsXml,
} from '@expo/config-plugins';
import type { ConfigPlugin } from '@expo/config-plugins';

const withUnity: ConfigPlugin = (config) => {
  // NOTE: this plugin only wires up the Android gradle/strings config needed to
  // include the exported `unityLibrary`. It must NOT touch `config.name` —
  // doing so would rename the consumer's app on every `expo prebuild`.
  config = withProjectBuildGradleMod(config);
  config = withSettingsGradleMod(config);
  config = withGradlePropertiesMod(config);
  config = withStringsXMLMod(config);
  return config;
};

const REPOSITORIES_END_LINE = `maven { url 'https://www.jitpack.io' }`;

const withProjectBuildGradleMod: ConfigPlugin = (config) =>
  withProjectBuildGradle(config, (modConfig) => {
    if (modConfig.modResults.contents.includes(REPOSITORIES_END_LINE)) {
      // use the last known line in expo's build.gradle file to append the newline after
      modConfig.modResults.contents = modConfig.modResults.contents.replace(
        REPOSITORIES_END_LINE,
        REPOSITORIES_END_LINE +
          '\nflatDir { dirs "${project(\':unityLibrary\').projectDir}/libs" }\n'
      );
    } else {
      throw new Error(
        'Failed to find the end of repositories in the android/build.gradle file`'
      );
    }
    return modConfig;
  });

const withSettingsGradleMod: ConfigPlugin = (config) =>
  withSettingsGradle(config, (modConfig) => {
    modConfig.modResults.contents += `
include ':unityLibrary'
project(':unityLibrary').projectDir=new File('../unity/builds/android/unityLibrary')
    `;
    return modConfig;
  });

const withGradlePropertiesMod: ConfigPlugin = (config) =>
  withGradleProperties(config, (modConfig) => {
    modConfig.modResults.push({
      type: 'property',
      key: 'unityStreamingAssets',
      value: '.unity3d',
    });
    return modConfig;
  });

// add string
const withStringsXMLMod: ConfigPlugin = (config) =>
  withStringsXml(config, (config) => {
    config.modResults = AndroidConfig.Strings.setStringItem(
      [
        {
          _: 'Game View',
          $: {
            name: 'game_view_content_description',
          },
        },
      ],
      config.modResults
    );
    return config;
  });

export default withUnity;

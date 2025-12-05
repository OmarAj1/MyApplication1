import {AppRegistry} from 'react-native';
import App from './src/App'; // Importing your main App component
import {name as appName} from './app.json';

// If you don't have app.json, replace appName with 'MyApplication2'
// Ensure this string matches the component name used in MainActivity.java
AppRegistry.registerComponent('MyApplication2', () => App);

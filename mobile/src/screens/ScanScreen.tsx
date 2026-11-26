import React, { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { CameraView, useCameraPermissions, type BarcodeScanningResult } from 'expo-camera';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { api, ApiError } from '../api/client';
import { colors, radius, spacing } from '../components/theme';
import { ErrorView } from '../components/StateViews';
import { productName, useI18n } from '../i18n';
import type { RootStackParamList } from '../navigation/types';

type Nav = NativeStackNavigationProp<RootStackParamList>;

type State =
  | { kind: 'scanning' }
  | { kind: 'looking'; code: string }
  | { kind: 'notFound'; code: string }
  | { kind: 'error'; error: unknown };

/** Scan a product barcode in the store and jump to its cross-store price comparison. */
export function ScanScreen() {
  const { t, language } = useI18n();
  const navigation = useNavigation<Nav>();
  const [permission, requestPermission] = useCameraPermissions();
  const [state, setState] = useState<State>({ kind: 'scanning' });
  // onBarcodeScanned fires many times per second; ignore frames while a lookup is running
  const busy = useRef(false);

  const onScanned = async ({ data }: BarcodeScanningResult) => {
    const code = data.replace(/\s/g, '');
    if (busy.current || !/^\d{6,14}$/.test(code)) return;
    busy.current = true;
    setState({ kind: 'looking', code });
    try {
      const product = await api.productByBarcode(code);
      navigation.replace('ProductDetails', { productId: product.id, title: productName(product, language) });
    } catch (e) {
      setState(e instanceof ApiError && e.status === 404 ? { kind: 'notFound', code } : { kind: 'error', error: e });
    }
  };

  const rescan = () => {
    busy.current = false;
    setState({ kind: 'scanning' });
  };

  if (!permission) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }
  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Text style={styles.message}>{t.scan.needPermission}</Text>
        <Pressable style={styles.button} onPress={requestPermission} accessibilityRole="button">
          <Text style={styles.buttonText}>{t.scan.grant}</Text>
        </Pressable>
      </View>
    );
  }
  if (state.kind === 'error') {
    return <ErrorView error={state.error} onRetry={rescan} />;
  }
  if (state.kind === 'notFound') {
    return (
      <View style={styles.center}>
        <Text style={styles.message}>{t.scan.notFound(state.code)}</Text>
        <Pressable style={styles.button} onPress={rescan} accessibilityRole="button">
          <Text style={styles.buttonText}>{t.scan.again}</Text>
        </Pressable>
        <Pressable
          style={styles.secondary}
          onPress={() => navigation.navigate('Tabs', { screen: 'SearchTab', params: {} })}
          accessibilityRole="button"
        >
          <Text style={styles.secondaryText}>{t.scan.searchByName}</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ['ean13', 'ean8', 'upc_a', 'upc_e'] }}
        onBarcodeScanned={state.kind === 'scanning' ? onScanned : undefined}
      />
      <View style={styles.overlay} pointerEvents="none">
        <View style={styles.frame} />
        <Text style={styles.hint}>{state.kind === 'looking' ? `${t.scan.looking} ${state.code}` : t.scan.hint}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000' },
  overlay: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
  frame: { width: 260, height: 160, borderWidth: 3, borderColor: '#fff', borderRadius: radius.md },
  hint: { marginTop: spacing.lg, color: '#fff', fontSize: 15, fontWeight: '600', textAlign: 'center',
    paddingHorizontal: spacing.lg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.background },
  message: { fontSize: 15, color: colors.text, textAlign: 'center', marginBottom: spacing.lg },
  button: { backgroundColor: colors.primary, paddingHorizontal: spacing.xl, paddingVertical: spacing.md, borderRadius: radius.sm },
  buttonText: { color: '#fff', fontWeight: '700', fontSize: 15 },
  secondary: { marginTop: spacing.md, padding: spacing.sm },
  secondaryText: { color: colors.primary, fontWeight: '600' },
});

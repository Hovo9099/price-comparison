import React from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { colors, spacing } from './theme';

interface Props {
  value?: string;
  placeholder: string;
  onChangeText?: (s: string) => void;
  onSubmit?: (s: string) => void;
  onPress?: () => void;
  autoFocus?: boolean;
}

/** Search input; with {@code onPress} it renders as a button that opens the search screen. */
export function SearchBar({ value, placeholder, onChangeText, onSubmit, onPress, autoFocus }: Props) {
  if (onPress) {
    return (
      <Pressable style={styles.box} onPress={onPress} accessibilityRole="search">
        <Text style={styles.icon}>🔍</Text>
        <Text style={styles.placeholder}>{placeholder}</Text>
      </Pressable>
    );
  }
  return (
    <View style={styles.box}>
      <Text style={styles.icon}>🔍</Text>
      <TextInput
        style={styles.input}
        value={value}
        placeholder={placeholder}
        placeholderTextColor={colors.muted}
        onChangeText={onChangeText}
        onSubmitEditing={(e) => onSubmit?.(e.nativeEvent.text)}
        returnKeyType="search"
        autoFocus={autoFocus}
        autoCorrect={false}
        clearButtonMode="while-editing"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.card,
    borderRadius: 14,
    paddingHorizontal: spacing.md,
    height: 50,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: '#0B1F17', shadowOpacity: 0.08, shadowRadius: 10, shadowOffset: { width: 0, height: 3 }, elevation: 3,
  },
  icon: { fontSize: 16, marginRight: spacing.sm },
  placeholder: { color: colors.muted, fontSize: 16 },
  input: { flex: 1, fontSize: 16, color: colors.text, height: '100%' },
});

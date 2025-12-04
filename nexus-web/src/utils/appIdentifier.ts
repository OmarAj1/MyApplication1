// Pure logic. No React here.
export const identifyApp = (pkg: string, type: string): { list: string, safety: string } => {
    let list = 'Misc';

    // 1. Vendor Identification
    if (pkg.includes('google') || pkg.includes('android.vending')) list = 'Google';
    else if (pkg.includes('facebook') || pkg.includes('fb')) list = 'Facebook';
    else if (pkg.includes('amazon')) list = 'Amazon';
    else if (pkg.includes('microsoft') || pkg.includes('office')) list = 'Microsoft';
    else if (pkg.includes('samsung') || pkg.includes('xiaomi') || pkg.includes('huawei')) list = 'OEM';
    else if (pkg.startsWith('com.android')) list = 'AOSP';

    // 2. Safety Logic
    let safety: 'Recommended' | 'Advanced' | 'Expert' | 'Unsafe' | 'Unknown' = 'Unknown';

    if (type === 'User') {
        safety = 'Recommended';
    } else {
        if (list === 'AOSP' || list === 'OEM') safety = 'Expert';
        else if (list === 'Google') safety = 'Advanced';
        else if (list === 'Facebook' || list === 'Amazon') safety = 'Recommended';
        else safety = 'Advanced';
    }

    return { list, safety };
}
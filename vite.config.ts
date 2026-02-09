import { UserConfigFn } from 'vite';
import { overrideVaadinConfig } from './vite.generated';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const customConfig: UserConfigFn = (env) => ({
  // 自定义 Vite 配置
  // https://vitejs.dev/config/
  resolve: {
    alias: {
      // Custom LineHeight plugin
      'custom-line-height': resolve(__dirname, 'frontend/plugins/line-height/index.ts')
    }
  }
});

export default overrideVaadinConfig(customConfig);

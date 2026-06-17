# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: rule-manager.spec.ts >> Rule Manager UI >> should open create modal and save new rule
- Location: tests/rule-manager.spec.ts:51:3

# Error details

```
Test timeout of 60000ms exceeded.
```

```
Error: page.waitForRequest: Test timeout of 60000ms exceeded.
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - banner [ref=e5]:
      - generic [ref=e6]:
        - heading "Rule Manager" [level=1] [ref=e7]:
          - img [ref=e9]
          - text: Rule Manager
        - paragraph [ref=e11]: Manage and deploy Spring Expression Language (SpEL) rules for audit streaming.
      - generic [ref=e12]:
        - button "Refresh rules" [ref=e13]:
          - img [ref=e14]
        - button "Add New Rule" [ref=e19]:
          - img [ref=e20]
          - generic [ref=e21]: Add New Rule
    - generic [ref=e22]:
      - generic [ref=e23]:
        - img [ref=e25]
        - generic [ref=e29]:
          - paragraph [ref=e30]: Total Rules
          - heading "2" [level=3] [ref=e31]
      - generic [ref=e32]:
        - img [ref=e34]
        - generic [ref=e37]:
          - paragraph [ref=e38]: Active
          - heading "1" [level=3] [ref=e39]
      - generic [ref=e40]:
        - img [ref=e42]
        - generic [ref=e46]:
          - paragraph [ref=e47]: Inactive
          - heading "1" [level=3] [ref=e48]
    - generic [ref=e49]:
      - generic [ref=e50]:
        - img [ref=e51]
        - textbox "Search by description or expression..." [ref=e54]
      - generic [ref=e56]:
        - button "All" [ref=e57]
        - button "Active" [ref=e58]
        - button "Inactive" [ref=e59]
    - table [ref=e62]:
      - rowgroup [ref=e63]:
        - row "Status Description SpEL Expression Last Updated Actions" [ref=e64]:
          - columnheader "Status" [ref=e65]
          - columnheader "Description" [ref=e66]
          - columnheader "SpEL Expression" [ref=e67]
          - columnheader "Last Updated" [ref=e68]
          - columnheader "Actions" [ref=e69]
      - rowgroup [ref=e70]:
        - 'row "Active High Value Transaction ID: 1 payload.amount > 1000 6/17/2026 3:00:00 AM Edit rule Delete rule" [ref=e71]':
          - cell "Active" [ref=e72]:
            - generic [ref=e73]:
              - img [ref=e74]
              - text: Active
          - 'cell "High Value Transaction ID: 1" [ref=e77]':
            - generic [ref=e78]: High Value Transaction
            - generic [ref=e79]: "ID: 1"
          - cell "payload.amount > 1000" [ref=e80]:
            - code [ref=e82]: payload.amount > 1000
          - cell "6/17/2026 3:00:00 AM" [ref=e83]:
            - generic [ref=e84]:
              - generic [ref=e85]: 6/17/2026
              - generic [ref=e86]: 3:00:00 AM
          - cell "Edit rule Delete rule" [ref=e87]:
            - generic [ref=e88]:
              - button "Edit rule" [ref=e89]:
                - img [ref=e90]
              - button "Delete rule" [ref=e93]:
                - img [ref=e94]
        - 'row "Inactive EU Region Check ID: 2 payload.region == ''EU'' 6/17/2026 3:05:00 AM Edit rule Delete rule" [ref=e97]':
          - cell "Inactive" [ref=e98]:
            - generic [ref=e99]:
              - img [ref=e100]
              - text: Inactive
          - 'cell "EU Region Check ID: 2" [ref=e104]':
            - generic [ref=e105]: EU Region Check
            - generic [ref=e106]: "ID: 2"
          - cell "payload.region == 'EU'" [ref=e107]:
            - code [ref=e109]: payload.region == 'EU'
          - cell "6/17/2026 3:05:00 AM" [ref=e110]:
            - generic [ref=e111]:
              - generic [ref=e112]: 6/17/2026
              - generic [ref=e113]: 3:05:00 AM
          - cell "Edit rule Delete rule" [ref=e114]:
            - generic [ref=e115]:
              - button "Edit rule" [ref=e116]:
                - img [ref=e117]
              - button "Delete rule" [ref=e120]:
                - img [ref=e121]
  - generic [ref=e125]:
    - generic [ref=e126]:
      - generic [ref=e127]:
        - heading "Create Rule" [level=3] [ref=e128]
        - paragraph [ref=e129]: Enter the details for your audit rule.
      - button [ref=e130]:
        - img [ref=e131]
    - generic [ref=e134]:
      - generic [ref=e135]:
        - generic [ref=e136]:
          - text: Description
          - textbox "Description" [ref=e137]:
            - /placeholder: Briefly describe the purpose of this rule
            - text: New Rule Description
        - generic [ref=e138]:
          - text: SpEL Expression
          - textbox "SpEL Expression" [active] [ref=e139]:
            - /placeholder: e.g. payload.amount > 1000 && payload.currency == 'USD'
            - text: payload.id != null
        - generic [ref=e140]:
          - checkbox "Active Rule" [checked] [ref=e141]
          - generic [ref=e142] [cursor=pointer]: Active Rule
      - generic [ref=e143]:
        - button "Discard" [ref=e144]
        - button "Save Rule" [ref=e145]:
          - img [ref=e146]
          - generic [ref=e150]: Save Rule
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test';
  2   | 
  3   | const mockRules = [
  4   |   {
  5   |     id: '1',
  6   |     description: 'High Value Transaction',
  7   |     spelExpression: "payload.amount > 1000",
  8   |     active: true,
  9   |     updatedAt: '2026-06-17T07:00:00Z'
  10  |   },
  11  |   {
  12  |     id: '2',
  13  |     description: 'EU Region Check',
  14  |     spelExpression: "payload.region == 'EU'",
  15  |     active: false,
  16  |     updatedAt: '2026-06-17T07:05:00Z'
  17  |   }
  18  | ];
  19  | 
  20  | test.describe('Rule Manager UI', () => {
  21  |   test.beforeEach(async ({ page }) => {
  22  |     // Mock API requests
  23  |     await page.route('/api/rules', async (route) => {
  24  |       if (route.request().method() === 'GET') {
  25  |         await route.fulfill({ json: mockRules });
  26  |       } else if (route.request().method() === 'POST') {
  27  |         const body = route.request().postDataJSON();
  28  |         await route.fulfill({ json: { ...body, id: '3', updatedAt: new Date().toISOString() } });
  29  |       }
  30  |     });
  31  | 
  32  |     await page.route('/api/rules/1', async (route) => {
  33  |       if (route.request().method() === 'PUT') {
  34  |         const body = route.request().postDataJSON();
  35  |         await route.fulfill({ json: { ...body, updatedAt: new Date().toISOString() } });
  36  |       } else if (route.request().method() === 'DELETE') {
  37  |         await route.fulfill({ status: 200 });
  38  |       }
  39  |     });
  40  | 
  41  |     await page.goto('/');
  42  |   });
  43  | 
  44  |   test('should display existing rules', async ({ page }) => {
  45  |     await expect(page.locator('h1')).toHaveText('Rule Manager');
  46  |     await expect(page.getByText('High Value Transaction')).toBeVisible();
  47  |     await expect(page.getByText('EU Region Check')).toBeVisible();
  48  |     await expect(page.getByText('payload.amount > 1000')).toBeVisible();
  49  |   });
  50  | 
  51  |   test('should open create modal and save new rule', async ({ page }) => {
  52  |     await page.getByRole('button', { name: 'Add New Rule' }).click();
  53  |     
  54  |     await expect(page.getByText('Create Rule')).toBeVisible();
  55  |     
  56  |     await page.fill('#description', 'New Rule Description');
  57  |     await page.fill('#expression', 'payload.id != null');
  58  |     
  59  |     // Intercept the POST request to verify it's called
> 60  |     const postRequest = page.waitForRequest(request => 
      |                              ^ Error: page.waitForRequest: Test timeout of 60000ms exceeded.
  61  |       request.url().includes('/api/rules') && request.method() === 'POST'
  62  |     );
  63  |     
  64  |     await page.getByRole('button', { name: 'Save Rule' }).click();
  65  |     
  66  |     const request = await postRequest;
  67  |     expect(request.postDataJSON()).toMatchObject({
  68  |       description: 'New Rule Description',
  69  |       spelExpression: 'payload.id != null',
  70  |       active: true
  71  |     });
  72  |     
  73  |     // Modal should be closed
  74  |     await expect(page.getByText('Create Rule')).not.toBeVisible();
  75  |   });
  76  | 
  77  |   test('should open edit modal and update rule', async ({ page }) => {
  78  |     // Click edit on the first rule
  79  |     await page.locator('tr').filter({ hasText: 'High Value Transaction' }).getByTitle('Edit rule').click();
  80  |     
  81  |     await expect(page.getByText('Edit Rule')).toBeVisible();
  82  |     await expect(page.locator('#description')).toHaveValue('High Value Transaction');
  83  |     
  84  |     await page.fill('#description', 'Updated Transaction Rule');
  85  |     
  86  |     const putRequest = page.waitForRequest(request => 
  87  |       request.url().includes('/api/rules/1') && request.method() === 'PUT'
  88  |     );
  89  |     
  90  |     await page.getByRole('button', { name: 'Update Rule' }).click();
  91  |     
  92  |     const request = await putRequest;
  93  |     expect(request.postDataJSON().description).toBe('Updated Transaction Rule');
  94  |   });
  95  | 
  96  |   test('should delete a rule', async ({ page }) => {
  97  |     // Mock window.confirm
  98  |     page.on('dialog', dialog => dialog.accept());
  99  |     
  100 |     const deleteRequest = page.waitForRequest(request => 
  101 |       request.url().includes('/api/rules/1') && request.method() === 'DELETE'
  102 |     );
  103 |     
  104 |     await page.locator('tr').filter({ hasText: 'High Value Transaction' }).getByTitle('Delete rule').click();
  105 |     
  106 |     await deleteRequest;
  107 |   });
  108 | 
  109 |   test('should display error message on API failure', async ({ page }) => {
  110 |     // Override the GET /api/rules mock to fail
  111 |     await page.route('/api/rules', async (route) => {
  112 |       if (route.request().method() === 'GET') {
  113 |         await route.fulfill({ status: 500, body: 'Internal Server Error' });
  114 |       }
  115 |     });
  116 | 
  117 |     await page.goto('/');
  118 |     
  119 |     await expect(page.getByText('Failed to fetch rules')).toBeVisible();
  120 |     await expect(page.getByText('Error')).toBeVisible();
  121 |   });
  122 | });
  123 | 
```